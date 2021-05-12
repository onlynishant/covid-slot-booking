import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Nishant Kumar
 * @date 12/05/21
 */
public class CaptchaWindow {
  private static String captchaText;
  private static final AtomicBoolean isButtonClick = new AtomicBoolean(false);

  public static void main(String[] args) {

  }

  public static String createWindow() throws IOException {
    isButtonClick.set(false);
    JFrame frame = new JFrame("Captcha");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    createUI(frame);
    frame.setSize(560, 200);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    while (!isButtonClick.get()) {
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
      }
    }
    return captchaText;
  }

  private static void createUI(final JFrame frame) throws IOException {
    JPanel panel = new JPanel();
    BorderLayout layout = new BorderLayout();
    panel.setLayout(layout);
    BufferedImage captchaImage = ImageIO.read(new File("captcha-new.png"));
    JLabel picLabel = new JLabel(new ImageIcon(captchaImage));
    panel.add(picLabel, BorderLayout.NORTH);

    JButton inputButton = new JButton("Submit");
    JTextField textField = new JTextField(10);

    Action action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        inputButton.doClick();
      }
    };

    textField.addActionListener(action);

    inputButton.setBackground(Color.BLACK);

    panel.add(textField, BorderLayout.CENTER);
    panel.add(inputButton, BorderLayout.PAGE_END);

    inputButton.addActionListener((ActionEvent e) -> {
      captchaText = textField.getText();
      System.out.println("User captcha Input: " + captchaText);
      frame.dispose();
      isButtonClick.set(true);
    });
//
//    panel.add(iconButton);
//    SwingUtilities.getRootPane(inputButton).setDefaultButton(inputButton);
    frame.getContentPane().add(panel, BorderLayout.CENTER);


  }
}
