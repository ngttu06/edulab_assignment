package vn.edu.khtn8.files;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class CommunityImagePolicyTest {
    private final String id="00000000-0000-4000-8000-000000000001",name="community-"+id+".jpg";
    private byte[] jpeg(int width,int height) throws Exception {
        var out=new ByteArrayOutputStream();assertTrue(ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"jpeg",out));return out.toByteArray();
    }
    @Test void scopeRequiresDedicatedServerGeneratedKeyNotJustFilename() {
        assertTrue(CommunityImagePolicy.communityKey("edulab/community/files/"+id));
        assertFalse(CommunityImagePolicy.communityKey("edulab/files/"+id));
        assertFalse(CommunityImagePolicy.communityKey("edulab/community/staging/"+id));
        assertFalse(CommunityImagePolicy.communityKey("../community/files/"+id));
        assertFalse(CommunityImagePolicy.communityKey(null));
        assertTrue(CommunityImagePolicy.communityName(name));
        assertFalse(CommunityImagePolicy.communityName("community-fake.jpg"));
    }
    @Test void onlySmallDecodableMetadataFreeJpegsPass() throws Exception {
        byte[] valid=jpeg(32,24);assertDoesNotThrow(()->CommunityImagePolicy.validate(name,"image/jpeg",valid));
        assertThrows(ResponseStatusException.class,()->CommunityImagePolicy.validateMetadata(name,"image/png",valid.length));
        assertThrows(ResponseStatusException.class,()->CommunityImagePolicy.validateMetadata(name,"image/jpeg",512001));
        assertThrows(ResponseStatusException.class,()->CommunityImagePolicy.validateMetadata(name,"image/jpeg",0));
        assertThrows(ResponseStatusException.class,()->CommunityImagePolicy.validate("one.jpg","image/jpeg",valid));
        assertThrows(ResponseStatusException.class,()->CommunityImagePolicy.validate(name,"image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,1}));
        byte[] large=jpeg(1601,1);assertThrows(ResponseStatusException.class,()->CommunityImagePolicy.validate(name,"image/jpeg",large));
        var exif=new ByteArrayOutputStream();exif.write(valid,0,2);exif.write(new byte[]{(byte)255,(byte)225,0,8,69,120,105,102,0,0});exif.write(valid,2,valid.length-2);
        assertThrows(ResponseStatusException.class,()->CommunityImagePolicy.validate(name,"image/jpeg",exif.toByteArray()));
    }
    @Test void browserIccAndOtherPrivateMetadataRemainRejectedByServer() throws Exception {
        byte[] valid=jpeg(32,24);
        for(int marker:new int[]{0xe0,0xe1,0xe2,0xed,0xee,0xfe}) {
            var metadata=new ByteArrayOutputStream();metadata.write(valid,0,2);
            metadata.write(new byte[]{(byte)255,(byte)marker,0,8,73,67,67,0,0,0});
            metadata.write(valid,2,valid.length-2);
            assertThrows(ResponseStatusException.class,()->CommunityImagePolicy.validate(name,"image/jpeg",metadata.toByteArray()),"Marker "+marker);
        }
    }
}