package vn.edu.khtn8.assessment;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class QuizDocumentReaderTest {
    byte[] docx(String content) throws IOException {
        var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }return out.toByteArray();
    }
    @Test void extractsVietnameseParagraphsAndTables() throws Exception {
        String text=QuizDocumentReader.docx(docx("<w:document xmlns:w='urn:word'><w:body><w:p><w:r><w:t>Câu 1: Nước</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>A. H₂O</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>"));
        assertTrue(text.contains("Câu 1: Nước\n"));assertTrue(text.contains("A. H₂O"));
    }
    @Test void rejectsDoctypeImagesAndOversizedContent() throws Exception {
        for(String xml:new String[]{"<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>","<w:document xmlns:w='urn:word'><w:drawing/></w:document>","<w:document xmlns:w='urn:word'><w:t>"+"a".repeat(15001)+"</w:t></w:document>"}) {
            byte[] bytes=docx(xml);assertThrows(ResponseStatusException.class,()->QuizDocumentReader.docx(bytes));
        }
    }
    @Test void rejectsFakeDocxAndZipBomb() throws Exception {
        assertThrows(ResponseStatusException.class,()->QuizDocumentReader.docx("fake".getBytes(StandardCharsets.UTF_8)));
        var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write(new byte[2*1024*1024+1]);}
        assertThrows(ResponseStatusException.class,()->QuizDocumentReader.docx(out.toByteArray()));
    }
}