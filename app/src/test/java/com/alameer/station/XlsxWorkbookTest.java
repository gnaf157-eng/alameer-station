package com.alameer.station.shifts;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class XlsxWorkbookTest {
    @Test public void workbookPreservesNumbersFormulasAndLiteralNames()throws Exception{
        File out=File.createTempFile("report-",".xlsx");
        try{
            XlsxWorkbook w=new XlsxWorkbook();
            w.row(true,"المخاريج","المقبوضات","الديون","الفلوس","البيان");
            w.row(false,null,1250.75,null,null,"=اسم & <عميل>\u0001");
            w.row(true,0,new XlsxWorkbook.Formula("SUM(B2:B2)",1250.75),0,0,"الإجمالي");
            w.write(out);
            try(ZipFile zip=new ZipFile(out)){
                assertEquals(6,zip.size());
                java.util.Enumeration<? extends ZipEntry> entries=zip.entries();
                while(entries.hasMoreElements()){
                    try(InputStream in=zip.getInputStream(entries.nextElement())){
                        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
                    }
                }
                Document sheet;
                try(InputStream in=zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))){
                    sheet=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
                }
                assertEquals("1",((Element)sheet.getElementsByTagName("sheetView").item(0)).getAttribute("rightToLeft"));
                assertEquals("1250.75",cell(sheet,"B2").getElementsByTagName("v").item(0).getTextContent());
                assertEquals("inlineStr",cell(sheet,"E2").getAttribute("t"));
                assertEquals("=اسم & <عميل>",cell(sheet,"E2").getTextContent());
                assertEquals("SUM(B2:B2)",cell(sheet,"B3").getElementsByTagName("f").item(0).getTextContent());
                assertEquals("1250.75",cell(sheet,"B3").getElementsByTagName("v").item(0).getTextContent());
                assertNull(cell(sheet,"A2"));
            }
        }finally{out.delete();}
    }
    @Test public void rejectsNonFiniteNumericData(){
        try{new XlsxWorkbook().row(false,Double.NaN);fail("Must reject NaN");}
        catch(IllegalArgumentException expected){}
    }
    private static Element cell(Document d,String address){
        NodeList list=d.getElementsByTagName("c");
        for(int i=0;i<list.getLength();i++){
            Element e=(Element)list.item(i);
            if(address.equals(e.getAttribute("r")))return e;
        }
        return null;
    }
}
