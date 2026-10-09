import java.nio.file.Files;
import java.nio.file.Path;
import vn.edu.khtn8.files.CommunityImagePolicy;

/** Checks browser-produced upload bodies against the real backend policy, offline. */
public class CommunityImageValidation {
    public static void main(String[] args) throws Exception {
        if(args.length==0) throw new IllegalArgumentException("Provide browser JPEG paths");
        String name="community-00000000-0000-4000-8000-000000000001.jpg";
        for(String file:args) {
            byte[] bytes=Files.readAllBytes(Path.of(file));
            CommunityImagePolicy.validate(name,"image/jpeg",bytes);
            System.out.println("PASS backend policy: "+Path.of(file).getFileName()+" ("+bytes.length+" bytes)");
        }
    }
}