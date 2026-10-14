package vn.edu.khtn8.assessment;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;

/** Bounded, non-rendering DOCX text reader. Never expands paths or follows external relationships. */
public final class QuizDocumentReader {
    private QuizDocumentReader() {}
    public static String docx(byte[] bytes) {
        if(bytes.length<4 || bytes[0]!='P' || bytes[1]!='K') throw AssessmentReviewService.bad("DOCX không đúng định dạng.");
        byte[] document=null;long expanded=0;int entries=0;Set<String> names=new HashSet<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName();
                if(++entries>500 || !names.add(name) || name.contains("..") || name.startsWith("/") || name.contains("\\") || name.toLowerCase(Locale.ROOT).contains("vbaproject")) throw AssessmentReviewService.bad("DOCX có nội dung không được hỗ trợ.");
                byte[] data=zip.readNBytes(2*1024*1024+1);expanded+=data.length;
                if(data.length>2*1024*1024 || expanded>15*1024*1024) throw AssessmentReviewService.bad("DOCX giải nén vượt giới hạn an toàn.");
                if(name.equals("word/document.xml")) document=data;
            }
            if(document==null || !names.contains("[Content_Types].xml")) throw AssessmentReviewService.bad("Không tìm thấy nội dung Word trong DOCX.");
            var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            var root=factory.newDocumentBuilder().parse(new ByteArrayInputStream(document));
            if(root.getElementsByTagNameNS("*","drawing").getLength()>0 || root.getElementsByTagNameNS("*","pict").getLength()>0 || root.getElementsByTagNameNS("*","oMath").getLength()>0)
                throw AssessmentReviewService.bad("Word chứa hình hoặc công thức không thể đọc đầy đủ. Hãy xuất PDF và nhập lại để AI đọc.");
            StringBuilder text=new StringBuilder();append(root.getDocumentElement(),text,0);
            if(text.toString().isBlank() || text.length()>15000) throw AssessmentReviewService.bad("Word trống hoặc quá dài (tối đa 15.000 ký tự mỗi lần nhập). Hãy chia tệp nhỏ hơn.");
            return text.toString();
        } catch(org.springframework.web.server.ResponseStatusException ex) {throw ex;}
        catch(Exception ex) {throw AssessmentReviewService.bad("Không đọc được DOCX. Hãy lưu lại bằng Word hoặc xuất PDF.");}
    }
    private static void append(Node node,StringBuilder text,int depth) {
        if(depth>100) throw AssessmentReviewService.bad("Cấu trúc Word quá phức tạp; hãy xuất PDF.");
        if("t".equals(node.getLocalName())) text.append(node.getTextContent());
        else {for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling()) append(child,text,depth+1);}
        if(Set.of("p","tr","br","tab").contains(node.getLocalName()==null?"":node.getLocalName())) text.append('\n');
        if(text.length()>15000) throw AssessmentReviewService.bad("Word quá dài; hãy chia tệp nhỏ hơn (15.000 ký tự/lần).");
    }
}