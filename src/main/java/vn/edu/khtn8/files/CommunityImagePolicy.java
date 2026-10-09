package vn.edu.khtn8.files;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Community-only JPEG validation; not an antivirus result. Scope is server-generated object keys. */
public final class CommunityImagePolicy {
    public static final long MAX_BYTES=512000;
    private static final String UUID="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
    private CommunityImagePolicy() {}
    public static boolean communityKey(String key) {
        return key!=null && key.matches("[A-Za-z0-9_-]{1,80}/community/files/"+UUID);
    }
    public static boolean communityName(Object filename) {
        return filename instanceof String name && name.matches("community-"+UUID+"\\.jpg");
    }
    public static void validateMetadata(String filename,String mime,long size) {
        if(!communityName(filename) || !"image/jpeg".equals(mime)) throw StorageSettingsService.bad("Cộng đồng chỉ nhận ảnh JPEG đã tối ưu.");
        FileValidator.validateMetadata(filename,mime,size,MAX_BYTES,MAX_BYTES);
    }
    public static void validate(String filename,String mime,byte[] bytes) {
        validateMetadata(filename,mime,bytes.length);
        FileValidator.validate(filename,mime,bytes,MAX_BYTES,MAX_BYTES);
        if(hasExif(bytes)) throw StorageSettingsService.bad("Ảnh không đúng định dạng đã tối ưu.");
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) throw StorageSettingsService.bad("Ảnh không đọc được.");
            var reader=readers.next();
            try {
                reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1 || h<1 || Math.max(w,h)>1600 || (long)w*h>2_560_000 || reader.read(0)==null)
                    throw StorageSettingsService.bad("Ảnh vượt kích thước cho phép.");
            } finally {reader.dispose();}
        } catch(IOException | IllegalArgumentException ex) {throw StorageSettingsService.bad("Ảnh không đọc được.");}
    }
    public static boolean hasExif(byte[] bytes) {
        if(bytes.length<4 || (bytes[0]&255)!=255 || (bytes[1]&255)!=216) return true;
        int pos=2;
        while(pos+3<bytes.length && (bytes[pos]&255)==255) {
            int marker=bytes[pos+1]&255;
            if(marker==0xda || marker==0xd9) return false;
            int length=((bytes[pos+2]&255)<<8)|(bytes[pos+3]&255);
            if(length<2 || pos+2+length>bytes.length) return true;
            if(marker==0xe1 || marker==0xe2 || marker==0xed || marker==0xfe || marker==0xe0 && !isJfif(bytes,pos+4,length-2) || marker==0xee) return true;
            pos+=length+2;
        }
        return true;
    }
    private static boolean isJfif(byte[] bytes,int offset,int size) {
        return size>=5 && bytes[offset]=='J' && bytes[offset+1]=='F' && bytes[offset+2]=='I' && bytes[offset+3]=='F' && bytes[offset+4]==0;
    }
}