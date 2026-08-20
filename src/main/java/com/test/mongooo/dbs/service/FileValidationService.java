package com.bank.dbs.service;

import com.bank.dbs.exception.EncryptedDocumentException;
import com.bank.dbs.exception.FormatMismatchException;
import com.bank.dbs.exception.VirusDetectedException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * FileValidationService.validate() — spec 5.1 Services table:
 * encryption check (PDFBox), magic-byte check, ClamAV socket scan (AC-BE-03/04/05).
 *
 * All three checks run against the assembled file already staged on the shared
 * NFS/EFS mount (post chunked-upload-assembly), not against in-memory bytes, to
 * avoid holding large files fully in heap (spec: files up to 25 MB, but the
 * streaming design should not assume that ceiling never changes).
 */
@Service
public class FileValidationService {

    private static final Logger log = LoggerFactory.getLogger(FileValidationService.class);

    /** First bytes of each supported format, used for magic-byte detection. */
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "pdf", new byte[]{0x25, 0x50, 0x44, 0x46},                     // %PDF
            "jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
            "tiff", new byte[]{0x49, 0x49, 0x2A, 0x00}                     // little-endian TIFF (II*\0)
    );

    private final String clamavHost;
    private final int clamavPort;

    public FileValidationService(@Value("${clamav.host}") String clamavHost,
                                  @Value("${clamav.port}") int clamavPort) {
        this.clamavHost = clamavHost;
        this.clamavPort = clamavPort;
    }

    /**
     * Runs all three checks in order (cheapest/most-specific first): magic bytes,
     * then encryption (PDF only), then antivirus. Throws the first violation found;
     * callers are expected to delete the staged file immediately on any exception
     * (AC-BE-03/AC-BE-05: "physical file deleted immediately after rejection").
     */
    public void validate(Path stagedFile, String declaredFormat, String filename) {
        checkMagicBytes(stagedFile, declaredFormat);
        if ("pdf".equalsIgnoreCase(declaredFormat)) {
            checkEncryption(stagedFile, filename);
        }
        scanAntivirus(stagedFile, filename);
    }

    void checkMagicBytes(Path stagedFile, String declaredFormat) {
        byte[] expected = MAGIC_BYTES.get(declaredFormat.toLowerCase());
        if (expected == null) {
            throw new FormatMismatchException(declaredFormat, "unsupported declared format");
        }

        byte[] actualPrefix = readFirstBytes(stagedFile, expected.length);
        if (actualPrefix.length < expected.length || !arraysStartWithMatch(actualPrefix, expected)) {
            throw new FormatMismatchException(declaredFormat, detectActualFormat(actualPrefix));
        }
    }

    void checkEncryption(Path stagedFile, String filename) {
        try (PDDocument document = Loader.loadPDF(stagedFile.toFile())) {
            if (document.isEncrypted()) {
                throw new EncryptedDocumentException(filename);
            }
        } catch (IOException e) {
            // A PDF that fails to parse at all is treated as a format mismatch, not
            // silently accepted.
            throw new FormatMismatchException("pdf", "unparseable PDF stream");
        }
    }

    void scanAntivirus(Path stagedFile, String filename) {
        try (Socket socket = new Socket(clamavHost, clamavPort);
             InputStream fileIn = Files.newInputStream(stagedFile)) {

            var out = socket.getOutputStream();
            // clamd INSTREAM protocol: size-prefixed chunks, terminated by a zero-length chunk.
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));

            byte[] buffer = new byte[8192];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                writeChunkSizePrefix(out, read);
                out.write(buffer, 0, read);
            }
            writeChunkSizePrefix(out, 0); // terminator
            out.flush();

            String response = readResponse(socket.getInputStream());
            if (response.contains("FOUND")) {
                String signature = response.replace("stream:", "").replace("FOUND", "").trim();
                Files.deleteIfExists(stagedFile);
                throw new VirusDetectedException(filename, signature);
            }
            if (!response.contains("OK")) {
                log.warn("Unexpected ClamAV response for {}: {}", filename, response);
            }
        } catch (VirusDetectedException e) {
            throw e;
        } catch (IOException e) {
            // R02 mitigation: if clamd is unreachable, fail closed rather than silently
            // skipping AV — a missed scan is worse than a rejected upload in a bank
            // context. Surface as a retryable 5xx via the generic handler.
            throw new IllegalStateException("Antivirus scan unavailable", e);
        }
    }

    private void writeChunkSizePrefix(java.io.OutputStream out, int size) throws IOException {
        out.write((size >>> 24) & 0xFF);
        out.write((size >>> 16) & 0xFF);
        out.write((size >>> 8) & 0xFF);
        out.write(size & 0xFF);
    }

    private String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[256];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
            if (buffer.toString(StandardCharsets.US_ASCII).contains("\0")) {
                break;
            }
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    private byte[] readFirstBytes(Path file, int n) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[n];
            int total = 0;
            int read;
            while (total < n && (read = in.read(buf, total, n - total)) != -1) {
                total += read;
            }
            return total == n ? buf : java.util.Arrays.copyOf(buf, total);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read staged file for validation", e);
        }
    }

    private boolean arraysStartWithMatch(byte[] actual, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private String detectActualFormat(byte[] prefix) {
        for (Map.Entry<String, byte[]> entry : MAGIC_BYTES.entrySet()) {
            byte[] sig = entry.getValue();
            if (prefix.length >= sig.length && arraysStartWithMatch(prefix, sig)) {
                return entry.getKey();
            }
        }
        return "unknown";
    }
}
