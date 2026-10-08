package vn.edu.khtn8.school;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static vn.edu.khtn8.school.AuthoringModels.*;

@Component
public class QuizCsv {
    private static final List<String> HEADER = List.of("question","option_a","option_b","option_c","option_d","correct_answer","explanation","points");
    private final Validator validator;
    public QuizCsv(Validator validator) { this.validator=validator; }
    public List<QuestionInput> parse(byte[] bytes) {
        if(bytes.length==0 || bytes.length>1024*1024) throw bad(1,"File phải có dữ liệu và không vượt quá 1 MB.");
        String text;
        try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException e) { throw bad(1,"Hãy xuất file CSV UTF-8."); }
        if(text.startsWith("\uFEFF")) text=text.substring(1);
        var rows=rows(text);
        if(rows.isEmpty() || !rows.getFirst().cells().equals(HEADER)) throw bad(1,"Tên/thứ tự cột không đúng. Hãy tải file mẫu.");
        var questions=new ArrayList<QuestionInput>();
        for(var row:rows.subList(1,rows.size())) {
            var c=row.cells();
            if(c.size()==1 && c.getFirst().isBlank()) continue;
            if(c.size()!=8) throw bad(row.line(),"Cần đúng 8 cột.");
            var options=new ArrayList<>(c.subList(1,5));
            while(!options.isEmpty() && options.getLast().isBlank()) options.removeLast();
            int correct="ABCD".indexOf(c.get(5).toUpperCase(java.util.Locale.ROOT));
            if(c.get(5).length()!=1 || correct<0 || correct>=options.size()) throw bad(row.line(),"Đáp án phải là A/B/C/D và trỏ tới lựa chọn có nội dung.");
            BigDecimal points;
            try { points=new BigDecimal(c.get(7)); } catch(NumberFormatException e) { throw bad(row.line(),"Điểm phải là số, dùng dấu chấm thập phân."); }
            var question=new QuestionInput(c.get(0),c.get(6),options,correct,points);
            if(!validator.validate(question).isEmpty()) throw bad(row.line(),"Câu hỏi/lựa chọn bị trống, quá dài hoặc điểm không nằm trong 0.01–9999.99 (tối đa 2 số thập phân).");
            questions.add(question);
            if(questions.size()>200) throw bad(row.line(),"Tối đa 200 câu hỏi.");
        }
        if(questions.isEmpty()) throw bad(2,"Cần ít nhất một câu hỏi.");
        return questions;
    }
    private record Row(int line,List<String> cells) { }
    private List<Row> rows(String text) {
        var result=new ArrayList<Row>(); var cells=new ArrayList<String>(); var cell=new StringBuilder();
        boolean quoted=false,closed=false; int line=1,start=1;
        for(int i=0;i<text.length();i++) {
            char ch=text.charAt(i);
            if(quoted) {
                if(ch=='"') { if(i+1<text.length() && text.charAt(i+1)=='"') {cell.append('"');i++;} else {quoted=false;closed=true;} }
                else {cell.append(ch);if(ch=='\n') line++;}
            } else if(ch=='"') {
                if(!cell.isEmpty() || closed) throw bad(line,"Dấu ngoặc kép không hợp lệ.");
                quoted=true;
            } else if(ch==',' || ch=='\n' || ch=='\r') {
                cells.add(cell.toString().trim()); cell.setLength(0);closed=false;
                if(ch!=',') {
                    result.add(new Row(start,List.copyOf(cells)));cells.clear();
                    if(ch=='\r' && i+1<text.length() && text.charAt(i+1)=='\n') i++;
                    line++; start=line;
                    if(result.size()>405) throw bad(line,"Quá nhiều dòng dữ liệu.");
                }
            } else {if(closed) throw bad(line,"Ký tự sau dấu ngoặc kép đóng không hợp lệ.");cell.append(ch);}
        }
        if(quoted) throw bad(start,"Thiếu dấu ngoặc kép đóng.");
        if(!cells.isEmpty() || !cell.isEmpty() || closed) {cells.add(cell.toString().trim());result.add(new Row(start,List.copyOf(cells)));}
        return result;
    }
    private static ResponseStatusException bad(int line,String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dòng "+line+": "+message);}
}