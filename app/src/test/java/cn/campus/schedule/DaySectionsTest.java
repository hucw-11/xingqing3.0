package cn.campus.schedule;
import org.junit.Test;
import static org.junit.Assert.*;
public class DaySectionsTest {
    @Test public void boundaries(){assertEquals(1,DaySections.group(false,0));assertEquals(1,DaySections.group(false,719));assertEquals(2,DaySections.group(false,720));assertEquals(2,DaySections.group(false,1079));assertEquals(3,DaySections.group(false,1080));assertEquals(3,DaySections.group(false,1439));assertEquals(0,DaySections.group(true,1080));}
}
