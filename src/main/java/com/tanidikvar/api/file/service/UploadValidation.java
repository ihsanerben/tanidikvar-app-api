package com.tanidikvar.api.file.service;
import com.tanidikvar.api.common.error.DomainException;
import com.tanidikvar.api.file.dto.PreparedUpload;
import java.io.*;
import java.awt.image.BufferedImage;
import java.security.*;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
@Service
public class UploadValidation {
 public PreparedUpload prepare(MultipartFile file,boolean avatar){
  long limit=(avatar?5:10)*1024L*1024;
  if(file.isEmpty()||file.getSize()>limit)throw invalid();
  try{
   byte[] bytes=file.getBytes();
   if(bytes.length==0||bytes.length>limit)throw invalid();
   if(avatar){
    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
     var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid();
     var reader=readers.next();
     try{
      String format=reader.getFormatName();if(!format.equalsIgnoreCase("JPEG")&&!format.equalsIgnoreCase("PNG"))throw invalid();
      reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
      if(w<1||h<1||(long)w*h>16_000_000)throw invalid();
      var source=reader.read(0);double ratio=Math.min(1,512.0/Math.max(w,h));
      var result=new BufferedImage(Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)),BufferedImage.TYPE_INT_ARGB);
      var graphics=result.createGraphics();try{graphics.drawImage(source,0,0,result.getWidth(),result.getHeight(),null);}finally{graphics.dispose();}
      var output=new ByteArrayOutputStream();ImageIO.write(result,"png",output);bytes=output.toByteArray();
     }finally{reader.dispose();}
    }
   }else{
    String start=new String(bytes,0,Math.min(8,bytes.length),java.nio.charset.StandardCharsets.US_ASCII);
    String end=new String(bytes,Math.max(0,bytes.length-1024),Math.min(1024,bytes.length),java.nio.charset.StandardCharsets.US_ASCII);
    if(!start.startsWith("%PDF-")||!end.contains("%%EOF"))throw invalid();
   }
   return new PreparedUpload(bytes,avatar?"image/png":"application/pdf",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
  }catch(IOException|NoSuchAlgorithmException|IllegalArgumentException ex){throw invalid();}
 }
 private DomainException invalid(){return new DomainException(400,"INVALID_FILE","Belge PDF ve en fazla 10 MB; fotoğraf JPEG/PNG ve en fazla 5 MB olmalı. Fotoğraf en fazla 16 milyon piksel olabilir.");}
}

