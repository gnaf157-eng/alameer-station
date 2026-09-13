package com.alameer.station.shifts;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Small, offline OOXML writer. Text stays text, including names beginning with '='. */
public final class XlsxWorkbook {
    private final StringBuilder rows=new StringBuilder();
    private int row;
    public int nextRow(){return row+1;}
    public static final class Formula {
        final String expression; final double value;
        public Formula(String expression,double value){this.expression=expression;this.value=value;}
    }
    public void row(boolean heading,Object... values){
        row++;
        rows.append("<row r=\"").append(row).append("\">");
        for(int i=0;i<values.length;i++){
            Object value=values[i];
            if(value==null)continue;
            String ref=column(i)+row;
            int style=heading?2:(value instanceof Number||value instanceof Formula?1:0);
            rows.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"");
            if(value instanceof Formula){
                Formula f=(Formula)value;
                rows.append("><f>").append(xml(f.expression)).append("</f><v>").append(number(f.value)).append("</v></c>");
            }else if(value instanceof Number){
                rows.append("><v>").append(number(((Number)value).doubleValue())).append("</v></c>");
            }else{
                rows.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value.toString())).append("</t></is></c>");
            }
        }
        rows.append("</row>");
    }
    private static String number(double n){
        if(Double.isNaN(n)||Double.isInfinite(n))throw new IllegalArgumentException("Invalid number");
        return Double.toString(n);
    }
    private static String column(int n){
        StringBuilder s=new StringBuilder();
        do{s.insert(0,(char)('A'+n%26));n=n/26-1;}while(n>=0);
        return s.toString();
    }
    private static String xml(String s){
        StringBuilder out=new StringBuilder();
        s.codePoints().forEach(c->{
            if(c==9||c==10||c==13||(c>=32&&c<=0xd7ff)||(c>=0xe000&&c<=0xfffd)||(c>=0x10000&&c<=0x10ffff)){
                if(c=='&')out.append("&amp;");else if(c=='<')out.append("&lt;");else if(c=='>')out.append("&gt;");
                else if(c=='"')out.append("&quot;");else out.appendCodePoint(c);
            }
        });
        return out.toString();
    }
    public void write(File file)throws IOException{
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){
            entry(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            entry(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"تقرير الوردية\" sheetId=\"1\" r:id=\"rId1\"/></sheets><calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>");
            entry(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            entry(zip,"xl/styles.xml","<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.##\"/></numFmts><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Arial\"/><color rgb=\"FF252A2E\"/></font><font><b/><sz val=\"11\"/><name val=\"Arial\"/><color rgb=\"FF252A2E\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFE99A\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\" wrapText=\"1\" readingOrder=\"2\"/></xf><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\"/></xf><xf numFmtId=\"164\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\" wrapText=\"1\" readingOrder=\"2\"/></xf></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            entry(zip,"xl/worksheets/sheet1.xml","<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr><dimension ref=\"A1:G"+Math.max(1,row)+"\"/><sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews><sheetFormatPr defaultRowHeight=\"22\"/><cols><col min=\"1\" max=\"4\" width=\"20\" customWidth=\"1\"/><col min=\"5\" max=\"5\" width=\"36\" customWidth=\"1\"/><col min=\"6\" max=\"7\" width=\"20\" customWidth=\"1\"/></cols><sheetData>"+rows+"</sheetData><pageMargins left=\"0.25\" right=\"0.25\" top=\"0.35\" bottom=\"0.35\" header=\"0.15\" footer=\"0.15\"/><pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/></worksheet>");
        }
    }
    private static void entry(ZipOutputStream zip,String name,String body)throws IOException{
        zip.putNextEntry(new ZipEntry(name));
        zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+body).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
