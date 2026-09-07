package io.github.lumicodex;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

/** 사용자가 요청한 시점에 꼬미가 있는 모니터 한 장만 캡처합니다. */
public final class DesktopCapture {
    private DesktopCapture() {}
    public static String capture(Point anchor) throws Exception {
        if(anchor==null) throw new IOException("꼬미의 모니터 위치를 찾지 못했습니다.");
        GraphicsDevice selected=null;
        for(var device:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if(device.getDefaultConfiguration().getBounds().contains(anchor)) { selected=device; break; }
        }
        if(selected==null) throw new IOException("꼬미가 있는 모니터를 찾지 못했습니다.");
        // Give the native context menu time to disappear before capturing.
        Thread.sleep(180);
        BufferedImage image=new Robot(selected).createScreenCapture(selected.getDefaultConfiguration().getBounds());
        return encode(image);
    }
    public static String encode(BufferedImage image) throws IOException {
        double scale=Math.min(1.0,1600.0/Math.max(image.getWidth(),image.getHeight()));
        BufferedImage result=image;
        if(scale<1) {
            result=new BufferedImage(Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
            Graphics2D g=result.createGraphics();
            try { g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC); g.drawImage(image,0,0,result.getWidth(),result.getHeight(),null); }
            finally { g.dispose(); }
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        if(!ImageIO.write(result,"png",bytes)) throw new IOException("PNG 변환 실패");
        if(bytes.size()>8*1024*1024) throw new IOException("화면 이미지가 너무 큽니다.");
        return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
