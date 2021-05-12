import net.sourceforge.tess4j.TesseractException;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.tesseract.TessBaseAPI;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_photo.fastNlMeansDenoising;


/**
 * @author Nishant Kumar
 * @date 11/05/21
 */
public class CaptchaSolver {

  static final int DELTA = 3;

  public static boolean isEligible(BufferedImage img, int x, int y) {

    int left = x - 1;
    while (left < 0 && x - left < 2 * DELTA) {
      if (img.getRGB(left, y) == Color.WHITE.getRGB()) {
        break;
      }
      left--;
    }
    if (left < 0) {
      return false;
    }
    int right = x + 1;

    while (right < img.getWidth() && right - left < 2 * DELTA) {
      if (img.getRGB(right, y) == Color.WHITE.getRGB()) {
        break;
      }
      right++;
    }
    if (right > img.getWidth()) {
      return false;
    }
    int top = y - 1;
    while (top > 0 && y - top < 2 * DELTA) {
      if (img.getRGB(x, top) == Color.WHITE.getRGB()) {
        break;
      }
      top--;
    }
    if (top < 0) {
      return false;
    }
    int bottom = y + 1;
    while (bottom < img.getHeight() && bottom - top < 2 * DELTA) {
      if (img.getRGB(x, bottom) == Color.WHITE.getRGB()) {
        break;
      }
      bottom++;
    }
    if (bottom > img.getHeight()) {
      return false;
    }


    int width = right - left;
    int height = bottom - top;
    if (width >= DELTA && height >= DELTA) {
      return true;
    }
    return false;

  }

  public static BufferedImage cleanImage(BufferedImage source) {
    BufferedImage clone = new BufferedImage(source.getWidth(),
        source.getHeight(), source.getType());
    Graphics2D g2d = clone.createGraphics();
    g2d.drawImage(source, 0, 0, null);
    g2d.dispose();
    for (int i = 0; i < clone.getWidth(); i++) {
      for (int j = 0; j < clone.getHeight(); j++) {
        int rgb = clone.getRGB(i, j);
        if (rgb == Color.WHITE.getRGB()) {
          continue;
        }
        if (isEligible(clone, i, j)) {
          continue;
        } else {
          clone.setRGB(i, j, Color.WHITE.getRGB());
        }

      }
    }

    return clone;

  }

  public static String cleanResult(String result) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < result.length(); i++) {
      if (Character.isAlphabetic(result.charAt(i)) || Character.isDigit(result.charAt(i))) {
        sb.append(result.charAt(i));
      }
    }
    return sb.toString();
  }

  public static void svgToPng(String fileName, String output) throws IOException, TranscoderException {
    String svg_URI_input = new File(fileName).toURL().toString();
    TranscoderInput input_svg_image = new TranscoderInput(svg_URI_input);
    //Step-2: Define OutputStream to PNG Image and attach to TranscoderOutput
    OutputStream png_ostream = new FileOutputStream(output);
    TranscoderOutput output_png_image = new TranscoderOutput(png_ostream);
    // Step-3: Create PNGTranscoder and define hints if required
    PNGTranscoder my_converter = new PNGTranscoder();
    // Step-4: Convert and Write output
    my_converter.transcode(input_svg_image, output_png_image);
    png_ostream.flush();
    png_ostream.close();
  }

  private TessBaseAPI tess;

  /**
   * Creates a new <code>CaptchaSolver</code> instance.
   *
   */
  public CaptchaSolver() {
    // use builtin resources
    this(CaptchaSolver.class.getResource("/tessdata").getPath());
  }

  /**
   * Creates a new <code>CaptchaSolver</code> instance.
   *
   * @param tessdataPath a <code>String</code> value
   */
  public CaptchaSolver(String tessdataPath) {
    tess = new TessBaseAPI();

    // Initialize tesseract-ocr with English, without specifying tessdata path
    if (tess.Init(tessdataPath, "eng") != 0) {
      throw new IllegalStateException("Could not initialize tesseract.");
    }
  }

  /**
   * Describe <code>finalize</code> method here.
   *
   */
  protected void finalize() {
    tess.End();
  }

  private Mat clean_captcha(String file) {

    // Load captcha captcha in grayscale
    Mat captcha = imread(file, IMREAD_UNCHANGED);

    if (captcha.empty()) {
      System.out.println("Can't read captcha image '" + file + "'");
      return captcha;
    }

    // Convert the captcha to black and white.
    Mat captcha_bw = new Mat();
//    threshold(captcha, captcha_bw, 128, 255, THRESH_BINARY | CV_THRESH_OTSU);

    // Erode the image to remove dot noise and that wierd line. I use a 3x3
    // rectengal as the kernal.
    Mat captcha_erode = new Mat();
    Mat element = getStructuringElement(MORPH_RECT, new Size(3, 3));
    erode(captcha_bw, captcha_erode, element);

    // Some cosmetic
    Mat captcha_denoise = new Mat();
    fastNlMeansDenoising(captcha_erode, captcha_denoise, 7, 7, 21);

    return captcha_denoise;
  }

  private String image_to_string(Mat img) {
    BytePointer outText;

    tess.SetImage(img.data(), img.cols(), img.rows(), 1, img.cols());

    outText = tess.GetUTF8Text();
    String s = outText.getString();

    // Destroy used object and release memory
    outText.deallocate();

    return s.replaceAll("[^0-9-A-Z]", "");
  }

  /**
   * Describe <code>solve</code> method here.
   *
   * @param file a <code>String</code> value
   * @return a <code>String</code> value
   */
  public String solve(String file) {
    return image_to_string(clean_captcha(file));
  }

  public static void main(String[] args) throws IOException, TranscoderException, TesseractException, InterruptedException {


    svgToPng("captcha.svg","captcha.png");

//    for (int j = 0; j < 10; j++) {
//      Toolkit.getDefaultToolkit().beep();
//      Thread.sleep(10);
//    }

    CaptchaSolver captchaSolver = new CaptchaSolver();
    System.out.println(captchaSolver.solve("captcha.png"));

//    String res = "{\"captcha\":\"<svg xmlns=\\\"http://www.w3.org/2000/svg\\\" width=\\\"150\\\" height=\\\"50\\\" viewBox=\\\"0,0,150,50\\\"><path d=\\\"M16 45 C91 45,88 29,148 29\\\" stroke=\\\"#444\\\" fill=\\\"none\\\"/><path fill=\\\"#222\\\" d=\\\"M87.58 14.24L87.65 14.31L87.68 14.34Q91.37 16.78 96.13 17.00L96.22 17.09L96.27 17.14Q100.84 17.26 105.10 15.50L105.07 15.47L105.04 15.44Q104.77 15.97 104.01 18.75L104.17 18.91L103.99 18.73Q101.32 19.83 98.35 19.98L98.30 19.94L98.30 19.93Q95.42 20.18 92.53 19.45L92.56 19.48L92.52 19.44Q93.26 23.12 93.46 26.77L93.39 26.71L93.33 26.65Q95.47 27.00 97.07 26.96L96.99 26.89L97.11 27.00Q98.13 26.81 100.53 26.69L100.60 26.76L100.57 26.73Q100.54 27.39 100.54 28.07L100.63 28.16L100.53 29.40L100.60 29.47Q98.27 29.53 93.47 29.61L93.44 29.58L93.32 29.46Q93.41 33.66 92.84 37.66L92.73 37.55L92.83 37.65Q95.44 36.98 98.18 37.09L98.15 37.06L98.20 37.12Q100.86 37.15 103.45 37.99L103.51 38.05L103.42 37.96Q103.56 38.82 103.83 39.58L103.82 39.57L104.28 41.10L104.23 41.05Q101.00 39.73 97.58 39.73L97.62 39.77L97.69 39.84Q92.79 39.82 88.53 42.06L88.59 42.12L88.57 42.10Q90.79 35.19 90.52 28.07L90.52 28.07L90.50 28.05Q90.23 20.88 87.57 14.22ZM88.11 42.87L87.96 42.71L88.11 42.87Q89.23 42.00 89.99 41.66L89.95 41.62L90.04 41.71Q89.82 42.44 89.25 43.96L89.32 44.03L89.35 44.07Q93.78 41.99 98.85 42.25L98.90 42.31L98.78 42.19Q104.05 42.58 108.04 45.28L108.02 45.26L108.00 45.24Q106.77 42.64 106.09 40.70L106.20 40.82L106.09 40.70Q105.10 40.09 104.11 39.75L104.29 39.93L104.29 39.93Q103.91 38.56 103.76 37.76L103.61 37.61L103.60 37.60Q100.55 36.65 97.39 36.65L97.34 36.59L97.39 36.65Q96.21 36.61 95.07 36.73L95.06 36.71L95.23 36.88Q95.19 35.48 95.23 34.26L95.35 34.38L95.45 31.89L95.40 31.84Q97.24 31.78 99.03 31.81L98.96 31.74L98.91 31.70Q100.73 31.73 102.52 31.88L102.55 31.91L102.66 32.02Q102.43 30.92 102.43 29.97L102.45 29.98L102.48 28.11L101.78 28.28L101.77 28.28Q101.26 28.26 100.80 28.26L100.95 28.41L100.93 28.39Q100.81 27.58 100.96 26.29L101.03 26.36L101.08 26.40Q99.51 26.66 98.33 26.70L98.19 26.56L98.30 26.67Q97.27 26.59 95.44 26.51L95.43 26.51L95.53 26.60Q95.44 25.18 95.25 22.37L95.26 22.38L95.10 22.21Q96.18 22.38 97.17 22.38L97.16 22.37L97.20 22.41Q101.96 22.45 105.65 20.55L105.52 20.42L105.62 20.51Q106.26 18.22 107.13 16.01L107.02 15.90L106.99 15.87Q105.73 16.77 104.89 17.12L104.83 17.05L104.90 17.13Q105.20 16.37 105.66 14.80L105.65 14.79L105.73 14.87Q101.04 16.96 96.16 16.65L96.20 16.69L96.10 16.59Q90.83 16.31 86.98 13.45L86.93 13.39L86.91 13.37Q89.80 20.34 90.10 27.95L90.12 27.96L90.20 28.05Q90.54 36.00 88.03 42.78Z\\\"/><path fill=\\\"#111\\\" d=\\\"M43.67 19.35L43.68 19.37L43.75 19.44Q44.52 23.59 44.67 27.17L44.74 27.24L44.65 27.15Q45.76 27.24 46.83 27.24L46.88 27.29L49.12 27.31L49.00 27.20Q50.00 27.13 50.87 25.72L50.90 25.75L50.90 25.75Q51.64 24.58 51.75 23.40L51.71 23.36L51.74 23.39Q52.21 20.47 47.99 19.98L48.00 19.99L48.03 20.03Q46.30 19.93 43.67 19.36ZM44.70 29.94L44.65 29.89L44.62 29.86Q44.58 36.10 43.43 40.51L43.46 40.54L43.50 40.58Q42.05 41.00 39.73 42.06L39.73 42.06L39.77 42.11Q42.07 35.38 41.80 28.11L41.89 28.20L41.88 28.19Q41.54 20.76 38.76 14.33L38.73 14.30L38.74 14.31Q42.83 17.03 49.07 17.03L49.03 16.99L49.05 17.01Q55.26 17.17 55.42 20.63L55.24 20.45L55.41 20.63Q55.31 22.89 54.32 25.55L54.34 25.57L54.47 25.70Q54.06 26.82 53.11 28.04L52.98 27.91L52.99 27.91Q51.67 29.53 49.12 29.79L49.19 29.87L49.20 29.88Q47.03 29.99 44.79 30.03ZM50.91 32.27L50.79 32.15L50.87 32.23Q55.04 32.47 56.29 27.68L56.21 27.60L56.27 27.65Q57.29 24.07 57.14 22.05L57.13 22.04L56.98 21.89Q57.00 20.50 56.24 19.51L56.10 19.37L56.07 19.35Q55.88 19.16 55.39 18.93L55.41 18.95L55.31 18.85Q55.36 18.75 54.75 17.99L54.74 17.98L54.77 18.00Q53.19 16.65 49.23 16.65L49.16 16.58L49.12 16.54Q42.28 16.59 38.05 13.43L38.05 13.43L38.12 13.50Q41.15 20.45 41.41 27.91L41.59 28.09L41.58 28.08Q41.84 35.73 39.18 42.81L39.09 42.72L39.13 42.76Q40.19 42.10 41.18 41.72L41.25 41.80L40.86 42.93L40.78 42.85Q40.70 43.53 40.43 44.10L40.35 44.02L40.29 43.96Q42.75 42.95 45.64 42.42L45.60 42.38L45.64 42.41Q46.58 37.00 46.66 32.28L46.59 32.21L46.63 32.25Q47.65 32.09 48.64 32.09L48.65 32.10L48.65 32.11Q50.07 32.19 50.87 32.23ZM49.79 22.17L49.91 22.28L49.98 22.35Q50.38 22.26 51.37 22.49L51.41 22.53L51.45 22.57Q51.54 22.77 51.58 23.08L51.50 23.00L51.55 23.05Q51.65 23.38 51.58 23.65L51.54 23.61L51.43 23.50Q51.42 24.83 50.70 25.70L50.67 25.67L50.62 25.62Q50.15 26.78 49.12 26.97L49.06 26.92L49.01 26.86Q48.22 26.79 46.58 26.79L46.64 26.86L46.69 26.91Q46.61 24.58 46.39 22.22L46.36 22.20L48.09 22.25L48.11 22.28Q49.05 22.38 49.93 22.30Z\\\"/><path fill=\\\"#333\\\" d=\\\"M120.25 40.32L120.15 40.22L120.24 40.32Q116.66 40.31 115.02 39.78L115.14 39.90L115.14 39.90Q113.06 39.15 112.75 35.76L112.73 35.73L114.14 34.63L114.20 34.69Q115.01 34.24 115.77 33.75L115.70 33.67L115.72 33.70Q115.50 35.65 117.10 36.87L116.95 36.72L117.12 36.89Q118.33 37.83 120.46 37.64L120.47 37.65L120.54 37.72Q124.84 37.22 124.65 33.68L124.61 33.65L124.71 33.74Q124.48 31.42 121.85 30.20L121.80 30.15L121.91 30.25Q119.09 29.23 116.73 28.01L116.66 27.94L116.75 28.03Q114.15 26.65 113.16 21.96L113.13 21.94L113.12 21.92Q112.99 21.45 112.88 20.69L112.95 20.76L112.94 20.76Q112.83 19.96 112.91 19.39L112.95 19.43L112.86 19.34Q113.00 17.73 114.26 17.28L114.27 17.29L114.28 17.30Q116.74 16.49 120.74 16.68L120.74 16.67L120.73 16.67Q122.44 16.67 123.24 16.74L123.25 16.75L123.40 16.90Q124.75 16.96 125.82 17.41L125.89 17.49L125.92 17.52Q127.95 17.91 128.18 20.39L128.23 20.43L128.16 20.37Q127.09 21.05 124.88 22.38L125.00 22.50L124.99 22.48Q124.45 19.36 120.08 19.36L120.19 19.47L120.14 19.43Q118.17 19.36 117.18 20.04L117.25 20.12L117.26 20.12Q116.05 20.59 116.28 22.38L116.38 22.49L116.27 22.37Q116.51 24.55 119.48 26.07L119.56 26.16L119.47 26.07Q120.07 26.37 124.57 28.00L124.60 28.03L124.48 27.92Q127.15 29.45 127.57 33.82L127.66 33.91L127.52 33.77Q127.56 33.96 127.63 35.22L127.76 35.34L127.63 35.22Q127.76 38.05 126.20 39.16L126.12 39.07L126.13 39.08Q124.34 40.11 120.15 40.22ZM122.42 42.57L122.46 42.60L122.50 42.65Q123.84 42.62 125.82 42.62L125.81 42.61L125.97 42.77Q128.08 42.79 129.34 42.37L129.35 42.38L129.18 42.21Q130.54 41.51 130.46 39.72L130.49 39.75L130.48 39.74Q130.30 38.61 129.92 36.55L130.04 36.68L130.00 36.63Q129.06 31.93 126.97 30.06L127.06 30.15L126.91 30.00Q126.27 28.56 124.94 27.84L124.78 27.68L119.58 25.72L119.61 25.75Q119.32 25.61 118.86 25.38L118.86 25.38L118.63 24.88L118.70 24.61L118.55 24.46Q118.40 23.06 119.54 22.45L119.66 22.56L119.59 22.50Q120.38 21.95 122.09 21.76L122.00 21.67L122.06 21.72Q123.22 21.63 124.36 22.09L124.38 22.11L124.34 22.06Q124.47 22.24 124.66 23.07L124.61 23.02L124.68 23.10Q124.78 22.85 125.28 22.62L125.43 22.77L125.26 22.60Q125.98 23.51 126.09 24.66L126.20 24.77L126.07 24.64Q126.26 24.71 129.92 22.12L129.84 22.05L129.89 22.10Q129.65 19.50 128.21 18.81L128.26 18.87L128.27 18.88Q127.55 17.47 126.10 16.94L126.09 16.92L126.09 16.93Q123.84 16.16 120.64 16.16L120.59 16.11L120.77 16.29Q115.71 16.14 113.84 16.82L114.01 16.99L113.83 16.81Q112.63 17.40 112.51 19.07L112.39 18.95L112.46 19.02Q112.47 19.60 112.89 21.81L112.93 21.85L112.85 21.77Q113.56 25.56 115.73 27.80L115.80 27.88L115.68 27.75Q116.56 29.56 118.07 30.24L118.10 30.28L117.92 30.09Q119.45 30.81 123.37 32.37L123.37 32.37L123.48 32.51L124.17 32.90L124.16 32.93L124.16 32.93Q124.33 33.37 124.37 33.71L124.26 33.59L124.25 33.59Q124.28 37.01 120.36 37.16L120.40 37.20L120.48 37.28Q119.37 37.35 118.08 36.97L117.92 36.81L118.08 36.98Q117.76 36.16 117.76 35.43L117.60 35.27L117.65 35.33Q117.67 35.08 117.71 34.81L117.73 34.83L117.72 34.82Q117.30 35.05 116.50 35.54L116.54 35.59L116.50 35.55Q116.08 34.51 116.23 33.14L116.22 33.14L116.12 33.03Q114.01 34.20 112.41 35.61L112.45 35.64L112.42 35.62Q112.52 36.48 112.60 37.51L112.54 37.45L112.55 37.46Q112.95 39.27 114.25 40.03L114.13 39.91L114.15 39.94Q115.50 41.89 118.05 42.27L117.91 42.14L117.92 42.14Q119.63 42.52 122.57 42.71Z\\\"/><path d=\\\"M13 18 C68 25,92 35,142 13\\\" stroke=\\\"#666\\\" fill=\\\"none\\\"/><path fill=\\\"#333\\\" d=\\\"M70.78 29.68L70.92 29.82L70.78 29.68Q66.57 29.47 66.00 32.51L66.14 32.65L66.02 32.54Q65.83 33.60 66.02 34.47L66.16 34.61L66.10 34.55Q66.12 35.34 66.58 36.75L66.58 36.75L66.50 36.67Q67.55 39.81 70.86 39.65L70.89 39.69L70.99 39.78Q72.98 39.83 74.31 38.27L74.29 38.25L74.22 38.18Q75.59 36.85 75.59 34.79L75.45 34.66L75.52 34.72Q75.62 34.10 75.47 33.03L75.58 33.14L75.54 33.10Q75.40 32.13 74.98 31.44L74.95 31.41L74.95 31.41Q73.37 29.79 70.78 29.68ZM75.69 48.22L75.70 48.23L75.72 48.25Q73.78 48.71 65.71 49.09L65.72 49.10L65.84 49.22Q64.17 49.30 62.54 48.46L62.46 48.39L62.50 48.42Q63.30 47.44 65.13 45.65L65.04 45.56L65.11 45.63Q67.61 46.76 69.70 46.57L69.68 46.55L69.60 46.46Q72.47 46.18 73.31 45.92L73.33 45.93L73.32 45.93Q75.64 45.31 75.64 43.52L75.55 43.43L75.64 43.53Q75.66 43.32 75.59 43.09L75.54 43.05L75.41 41.43L75.37 41.39Q75.42 40.67 75.42 39.87L75.38 39.84L75.47 39.93Q74.06 42.13 70.67 42.13L70.56 42.02L70.64 42.10Q66.72 42.06 65.08 39.86L65.03 39.81L65.13 39.91Q63.96 38.31 63.16 33.89L63.26 34.00L63.28 34.02Q62.83 32.35 62.83 30.94L62.98 31.09L62.87 30.98Q62.95 29.27 63.86 28.28L63.86 28.28L63.77 28.19Q65.48 26.81 70.12 26.81L70.05 26.74L71.85 26.98L71.92 27.05Q75.13 27.41 76.46 29.35L76.37 29.25L76.37 29.25Q76.69 28.54 77.11 26.91L77.09 26.89L77.01 26.81Q78.92 26.51 80.67 25.79L80.68 25.80L80.57 25.69Q78.02 31.89 78.02 39.12L78.03 39.14L77.97 39.08Q77.87 41.64 78.25 44.27L78.23 44.25L78.25 44.26Q78.41 44.93 78.34 45.61L78.43 45.71L78.53 45.80Q78.24 47.07 77.02 47.83L76.97 47.78L76.97 47.78Q76.55 48.12 75.71 48.24ZM78.75 51.24L78.61 51.10L78.66 51.15Q80.20 51.28 80.96 50.33L80.99 50.36L80.84 50.21Q81.17 49.24 81.06 48.52L81.14 48.60L81.07 48.54Q81.00 47.93 80.81 47.17L80.75 47.11L80.80 47.16Q79.54 41.90 79.88 36.65L79.95 36.72L79.97 36.74Q80.28 31.27 82.42 26.43L82.51 26.52L80.27 27.33L80.32 27.38Q80.58 26.80 80.81 26.23L80.84 26.27L81.28 25.07L81.21 25.00Q78.92 26.02 76.72 26.44L76.66 26.39L76.67 26.40Q76.47 27.37 76.32 28.40L76.28 28.36L76.36 28.45Q74.14 26.41 70.10 26.41L70.14 26.45L68.27 26.33L68.39 26.45Q65.13 26.42 63.45 27.83L63.54 27.92L63.52 27.90Q62.55 28.83 62.59 30.74L62.48 30.63L62.54 30.69Q62.63 33.22 63.66 37.48L63.57 37.39L63.66 37.48Q64.21 39.48 65.42 40.88L65.39 40.85L65.57 41.03L65.70 41.16L65.68 41.14Q66.82 43.46 69.41 43.99L69.46 44.04L69.48 44.06Q70.89 44.37 72.34 44.41L72.33 44.40L72.24 44.31Q73.98 44.34 75.05 43.96L75.00 43.91L75.08 43.99Q74.46 45.54 71.79 45.88L71.75 45.84L71.76 45.85Q70.80 46.11 70.12 46.11L70.11 46.11L69.69 46.06L69.65 46.02Q66.87 46.06 65.19 45.10L65.10 45.02L63.53 46.87L63.52 46.86Q62.68 47.69 61.84 48.64L61.87 48.68L61.74 48.54Q62.70 49.04 63.69 49.23L63.86 49.41L62.95 50.25L62.84 50.14Q65.53 51.19 71.17 51.19L71.17 51.20L71.51 51.30L71.38 51.18Q75.09 51.28 78.67 51.16ZM72.69 32.01L72.66 31.98L72.77 32.09Q74.03 31.97 74.94 32.39L74.96 32.41L74.88 32.33Q75.08 32.84 75.16 33.52L75.13 33.49L75.14 33.51Q75.18 33.81 75.14 34.72L75.17 34.76L75.19 34.77Q75.20 36.77 74.10 38.06L74.03 38.00L73.93 37.89Q72.79 39.35 70.89 39.31L71.02 39.43L70.88 39.29Q69.53 39.32 68.70 38.90L68.58 38.79L68.72 38.93Q68.23 37.68 68.19 36.65L68.32 36.78L68.24 36.70Q68.13 32.36 72.74 32.06Z\\\"/><path d=\\\"M18 10 C68 41,57 7,138 8\\\" stroke=\\\"#333\\\" fill=\\\"none\\\"/><path fill=\\\"#333\\\" d=\\\"M13.70 14.36L13.55 14.21L13.64 14.29Q17.54 16.94 22.30 17.17L22.30 17.17L22.18 17.05Q26.80 17.22 31.06 15.47L31.12 15.52L31.14 15.54Q30.84 16.04 30.08 18.82L30.01 18.75L30.17 18.91Q27.42 19.93 24.45 20.08L24.38 20.01L24.38 20.01Q21.31 20.07 18.42 19.34L18.54 19.46L18.42 19.34Q19.19 23.04 19.38 26.70L19.31 26.63L19.32 26.63Q21.38 26.91 22.98 26.87L22.93 26.83L22.97 26.86Q24.27 26.95 26.67 26.83L26.57 26.73L26.71 26.88Q26.54 27.38 26.54 28.07L26.52 28.06L26.62 29.48L26.52 29.39Q24.21 29.48 19.42 29.55L19.40 29.53L19.36 29.49Q19.38 33.63 18.81 37.62L18.78 37.59L18.74 37.55Q21.43 36.97 24.17 37.09L24.20 37.11L24.20 37.11Q26.99 37.28 29.58 38.11L29.40 37.94L29.40 37.94Q29.58 38.84 29.85 39.60L29.85 39.60L30.35 41.17L30.35 41.17Q27.09 39.82 23.67 39.82L23.65 39.80L23.58 39.73Q18.75 39.77 14.48 42.02L14.57 42.10L14.57 42.11Q16.87 35.27 16.61 28.15L16.64 28.19L16.57 28.12Q16.26 20.91 13.59 14.25ZM13.97 42.72L13.94 42.69L13.99 42.74Q15.33 42.10 16.09 41.76L15.99 41.66L16.09 41.76Q15.86 42.48 15.29 44.01L15.33 44.04L15.29 44.00Q19.91 42.12 24.97 42.38L24.80 42.20L24.89 42.29Q30.04 42.57 34.03 45.28L33.99 45.23L34.06 45.30Q32.89 42.76 32.21 40.82L32.25 40.86L32.12 40.73Q31.29 40.28 30.30 39.94L30.28 39.92L30.13 39.77Q29.73 38.38 29.58 37.59L29.68 37.68L29.62 37.62Q26.51 36.61 23.35 36.61L23.37 36.62L23.38 36.64Q22.29 36.69 21.15 36.81L21.12 36.77L21.18 36.84Q21.27 35.56 21.31 34.34L21.37 34.40L21.33 31.77L21.43 31.87Q23.16 31.69 24.95 31.73L24.92 31.70L25.03 31.82Q26.85 31.85 28.64 32.00L28.57 31.92L28.65 32.01Q28.51 30.99 28.51 30.04L28.53 30.06L28.50 28.13L27.77 28.27L27.77 28.27Q27.23 28.23 26.77 28.23L26.93 28.39L26.87 28.32Q26.91 27.68 27.06 26.39L26.94 26.26L26.96 26.28Q25.47 26.62 24.29 26.66L24.20 26.57L24.20 26.57Q23.22 26.54 21.40 26.47L21.39 26.47L21.45 26.53Q21.35 25.09 21.16 22.28L21.16 22.27L21.16 22.27Q22.25 22.45 23.24 22.45L23.28 22.49L23.16 22.37Q27.89 22.38 31.59 20.48L31.66 20.56L31.67 20.57Q32.17 18.13 33.04 15.92L33.12 16.00L33.05 15.93Q31.66 16.71 30.82 17.05L30.80 17.03L30.87 17.10Q31.15 16.32 31.61 14.76L31.73 14.87L31.59 14.74Q26.97 16.89 22.10 16.59L22.13 16.62L22.09 16.58Q16.85 16.32 13.00 13.47L12.97 13.44L12.97 13.44Q15.97 20.51 16.28 28.13L16.22 28.07L16.23 28.08Q16.55 36.02 14.04 42.79Z\\\"/></svg>\"}";
//
//    Map<String, Object> map = Runner.jsonMapper.readValue(res, Map.class);
//
//    System.out.println(map.get("captcha"));
//
//    FileWriter fileWriter = new FileWriter("captcha-new.svg");
//    fileWriter.write(map.get("captcha").toString().toCharArray());
//    fileWriter.close();
//
//    svgToPng("captcha-new.svg");
//    Desktop.getDesktop().open(new File("captcha-new.png"));
//
//    System.out.println("Enter captcha:");
//    Scanner sc = new Scanner(System.in);
//
//    String s = sc.next();
//
//    System.out.println(s);

//    System.loadLibrary("tesseract");
//
//    BufferedImage image = ImageIO.read(new File("captcha.png"));
//    BufferedImage clean = cleanImage(image);
//    ImageIO.write(clean, "png", new File("clean.png"));
//    Tesseract tesseract = new Tesseract();
//    tesseract.setDatapath(CaptchaSolver.class.getResource("/tessdata").getPath());
//    String result = tesseract.doOCR(image);
//    result = cleanResult(result);
//
//    System.out.println(result);
//
//    System.out.println("result : " + result);

//    CaptchaSolver cs = new CaptchaSolver();
//    System.out.println("Captcha: " + cs.solve("captcha.png"));

//    TwoCaptcha solver = new TwoCaptcha("40207c54a63714c2c68b7070f88eaa3e");
//
//    com.twocaptcha.captcha.Normal captcha = new Normal();
//    captcha.setFile("captcha.png");
//    captcha.setNumeric(4);
//    captcha.setMinLen(4);
//    captcha.setMaxLen(20);
//    captcha.setPhrase(true);
//    captcha.setCaseSensitive(true);
//    captcha.setCalc(false);
//    captcha.setLang("en");
////    captcha.setHintImg(new File("path/to/hint.jpg"));
////    captcha.setHintText("Type red symbols only");
//
//    try {
//      solver.solve(captcha);
//      System.out.println("Captcha solved: " + captcha.getCode());
//    } catch (Exception e) {
//      System.out.println("Error occurred: " + e.getMessage());
//    }
  }
}
