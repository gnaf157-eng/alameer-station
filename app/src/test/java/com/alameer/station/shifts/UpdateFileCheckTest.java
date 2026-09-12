package com.alameer.station.shifts;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.zip.*;
public class UpdateFileCheckTest{
 private File apk(boolean manifest)throws Exception{File f=File.createTempFile("station",".apk");f.deleteOnExit();try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){z.putNextEntry(new ZipEntry(manifest?"AndroidManifest.xml":"nested/app.apk"));z.write(1);z.closeEntry();z.putNextEntry(new ZipEntry("classes.dex"));z.write(2);z.closeEntry();}return f;}
 @Test public void acceptsApkContainer()throws Exception{File f=apk(true);UpdateFileCheck.verify(f,f.length(),"");}
 @Test public void rejectsArtifactZip()throws Exception{try{UpdateFileCheck.verify(apk(false),0,"");fail();}catch(IOException expected){}}
 @Test public void rejectsWrongSize()throws Exception{try{UpdateFileCheck.verify(apk(true),1,"");fail();}catch(IOException expected){}}
 @Test public void rejectsWrongHash()throws Exception{try{UpdateFileCheck.verify(apk(true),0,"deadbeef");fail();}catch(IOException expected){}}
 @Test public void rejectsHtml()throws Exception{File f=File.createTempFile("station",".apk");f.deleteOnExit();try(FileWriter w=new FileWriter(f)){w.write("<html>Download confirmation</html>");}try{UpdateFileCheck.verify(f,0,"");fail();}catch(IOException expected){}}
}
