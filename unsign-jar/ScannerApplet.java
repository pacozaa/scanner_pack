import java.lang.reflect.InvocationTargetException;

import java.lang.reflect.Method;


import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfWriter;

import eu.gnome.morena.Configuration;
import eu.gnome.morena.Device;

import eu.gnome.morena.Manager;

import eu.gnome.morena.Scanner;

import java.applet.Applet;

import java.awt.image.BufferedImage;

import java.io.File;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import java.io.IOException;

import java.net.MalformedURLException;
import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class ScannerApplet extends Applet {
    final static float horizontal = 8.27f;
    final static float vertical = 11.69f;
    static Manager manager;
    static String defaultTypeScan = "batch";
    static String defaultDevice = "hp scanjet 5590";
    static int defaultMode = Scanner.RGB_16;
    static int defaultResolution = 300;
    static int defaultPaperWidth = 2481;
    static int defaultPaperHight = 3508;
    static boolean defaultDuplexScan = false;
    static int pageNo = 1;
    public ScannerApplet()
    {
        super();
       
    }
    @Override
    public void init() {
       AccessController.doPrivileged(new PrivilegedAction() {
                   public Object run() {
                      LoadScanner();
                      return null;
                   }
               });
    }
    public void LoadScanner(){
        

        String selectedDevice = this.getParameter("ParamDevice");

        if (selectedDevice != null && selectedDevice.length() > 0) {
            defaultDevice = selectedDevice;
        }

        String selectedMode = this.getParameter("ParamMode");

        if (selectedMode != null && selectedMode.length() > 0) {
            if (selectedMode.equals("GRAY")) {
                defaultMode = Scanner.GRAY_16;
            }
        }

        String selectedResolution = this.getParameter("ParamResolution");

        if (selectedResolution != null && selectedResolution.length() > 0) {
            defaultResolution = Integer.parseInt(selectedResolution);
            if (defaultResolution != 300) {
                defaultPaperWidth = Math.round(horizontal * defaultResolution);
                defaultPaperHight = Math.round(vertical * defaultResolution);
            }

        }

        String selectedDuplex = this.getParameter("ParamDuplex");

        if (selectedDuplex != null && selectedDuplex.length() > 0) {
            defaultDuplexScan = Boolean.parseBoolean(selectedDuplex);
        }

        try {
            Configuration.addDeviceType(".*" + defaultDevice + ".*", true);
            manager = Manager.getInstance();
        } catch (Throwable ex) {
            ex.printStackTrace();
        } finally {
            manager.close();
        }


    }

    public void Scan() throws FileNotFoundException, IOException, Exception, InvocationTargetException {
        List<? extends Device> devices = manager.listDevices();
        List<File> filesToDelete = new ArrayList<File>();
        List<File> filesToPDF = new ArrayList<File>();

        if (devices.size() > 0) {
            for (Device device : devices) {
                if (device.toString().equals(defaultDevice)) {
                    if (device != null) {
                        if (device instanceof Scanner) {
                            Scanner scanner = (Scanner) device;
                            scanner.setMode(defaultMode);
                            scanner.setResolution(defaultResolution);
                            scanner.setFrame(0, 0, defaultPaperWidth, defaultPaperHight);

                            int feederUnit = scanner.getFeederFunctionalUnit();
                            if (feederUnit < 0) {
                                feederUnit = 0; // 0 designates a default unit
                            }

                            if (scanner.isDuplexSupported()) {
                                scanner.setDuplexEnabled(defaultDuplexScan);
                            }

                            pageNo = 1;

                            ScanSession session = new ScanSession();
                            try {
                                session.startSession(device, feederUnit);

                                File file = null;

                                while (null != (file = session.getImageFile())) {
                                    filesToDelete.add(file);
                                    BufferedImage image = ImageIO.read(file);
                                    if (!"jpg".equalsIgnoreCase(ScanSession.getExt(file))) { // convert to jpeg if not already in jpeg format
                                        File jpgFile = new File(file.getParent(), "img_" + (pageNo++) + ".jpg");
                                        FileOutputStream fout = new FileOutputStream(jpgFile);
                                        ImageIO.write(image, "jpeg", fout);
                                        fout.close();

                                        filesToPDF.add(jpgFile);
                                        filesToDelete.add(jpgFile);
                                    }
                                }
                            } catch(InvocationTargetException ed){
                                ed.printStackTrace();
                            }catch (Exception ex) { // check if error is related to empty ADF
                                if (session.isEmptyFeeder())
                                    System.out.println("No more sheets in the document feeder");
                                else
                                    ex.printStackTrace();
                            } 
                            
                            if(filesToDelete.size() == 0){
                                File imageFile = SynchronousHelper.scanFile(device);
                                BufferedImage bimage = SynchronousHelper.scanImage(device);
                                File jpgFile = new File(imageFile.getParent(), "img_" + (pageNo++) + ".jpg");
                                FileOutputStream fout = new FileOutputStream(jpgFile);
                                ImageIO.write(bimage, "jpeg", fout);
                                fout.close();
                                
                                filesToPDF.add(jpgFile);
                                filesToDelete.add(jpgFile);
                            }

                            try {
                                Thread.sleep(10000);

                                Document document = new Document();

                                PdfWriter.getInstance(document, new FileOutputStream("scan.pdf"));
                                document.setPageSize(PageSize.A4);
                                document.setMargins(0, 0, 0, 0);

                                document.open();

                                Image image1;
                                for (File file : filesToPDF) {
                                    System.err.println(file.getPath());

                                    image1 = Image.getInstance(file.getPath());

                                    image1.scaleToFit(PageSize.A4.getWidth(), PageSize.A4.getHeight());
                                    document.add(image1);
                                }
                                document.close();
                                for (File file : filesToDelete) {
                                    file.deleteOnExit();
                                }
                            } catch (InterruptedException e) {
                                System.out.println(e.getStackTrace());
                            } catch (DocumentException e) {
                                System.out.println(e.getStackTrace());
                            } catch (FileNotFoundException e) {
                                System.out.println(e.getStackTrace());
                            } catch (MalformedURLException e) {
                                System.out.println(e.getStackTrace());
                            } catch (IOException e) {
                                System.out.println(e.getStackTrace());
                            }

                        }
                    }
                    break;
                }
            }
        }
    }
}
