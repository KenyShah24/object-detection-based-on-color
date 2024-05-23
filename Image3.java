import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.List;
import javax.swing.*;
import java.util.LinkedList;
import java.util.Queue;

import javax.imageio.ImageIO;

public class Image3 {
    BufferedImage inputImage;
    BufferedImage objectImages[];
    int width = 640;
    int height = 480;
    JFrame frame;
    JLabel lbIm1;
    List<Point> objectPixels;
    List<List<Point>> clusteredPixelsFromMatrix = new ArrayList<>();
    List<List<Point>> filteredClusteredPixelsFromMatrix = new ArrayList<>();
    double thresholdDistance = 100.0; // Adjust this threshold as needed
    int clusterThreshold = 2000;
    static int satHist[], valHist[];
    private String currentObject;
    Graphics g;

    public static void main(String[] args) {
        Image3 ren = new Image3();
        ren.showIms(args);
    }

    public void showIms(String[] args) {
        objectPixels = new ArrayList<>();
        inputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        readImageRGB(width, height, args[0], inputImage);
        g = inputImage.getGraphics();
        int noOfObjects = args.length - 1;
        objectImages = new BufferedImage[noOfObjects];
        for (int i = 0; i < noOfObjects; i++) {
            currentObject = args[i + 1].substring(args[i + 1].lastIndexOf("/") + 1);
            currentObject = currentObject.substring(0, currentObject.indexOf(".", 3));
            objectImages[i] = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            readImageRGB(width, height, args[i + 1], objectImages[i]);
            objectDetection(objectImages[i]);
        }

        frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);

        lbIm1 = new JLabel(new ImageIcon(inputImage));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        frame.pack();
        frame.setVisible(true);
        System.out.println("Object Detection complete.");

    }

    private void objectDetection(BufferedImage objectImage) {
        boolean volleyColor1 = false;
        boolean volleyColor2 = false;
        objectPixels = new ArrayList<>();
        objectImage = convertToHSV(objectImage);
        List<Integer> xVals = new ArrayList<>();
        List<Integer> yVals = new ArrayList<>();

        // Calculate the histogram of the object image
        int[] histogram = calculateHistogram(objectImage);

        // Find the peak Hue value in the histogram
        int peakHue = findPeakHue(histogram);
        if (peakHue >= 0 && peakHue <= 40 || peakHue >= 350 && peakHue <= 355) {
            int[] histogramV = calculateHistogramV(objectImage);
            findObjectByV(histogramV);

        } else {
            int[] dominantColors = findDominantColors(histogram, 10, histogram.length);
            for (int i = 0; i < dominantColors.length; i++) {
                if (dominantColors[i] >= 47 && dominantColors[i] <= 50) {
                    volleyColor1 = true;
                }
                if ((dominantColors[i] >= 225 && dominantColors[i] <= 230)) {
                    volleyColor2 = true;
                }
            }
            if (volleyColor1 && volleyColor2) {
                int[] histogramU = calculateHistogramU(objectImage);
                findObjectByU(histogramU, histogram);
            } else {
                int[] dominantColorsGen = findDominantColors(histogram, 4, histogram.length);
                int[] dominantColorsSat = findDominantColors(satHist, 4, satHist.length);
                int[] dominantColorsVal = findDominantColors(valHist, 4, valHist.length);
                // Define the hue range for the object you want to colorize (e.g., Pikachu)
                int hueThreshold = 10; // Adjust this threshold as needed
                int satThreshold = 30; // Adjust this threshold as needed
                int valThreshold = 10; // Adjust this threshold as needed
                // Process each pixel in the input image
                for (int y = 0; y < inputImage.getHeight(); y++) {
                    for (int x = 0; x < inputImage.getWidth(); x++) { // Get the color of the pixel in the input image
                        Color pixelColor = new Color(inputImage.getRGB(x, y));

                        // Convert the pixel color to HSV
                        float[] hsv = new float[3];
                        Color.RGBtoHSB(pixelColor.getRed(), pixelColor.getGreen(), pixelColor.getBlue(), hsv);

                        // Get the hue value (0-360 degrees)
                        int hue = (int) (hsv[0] * 360);
                        int sat = (int) (hsv[1] * 100);
                        int val = (int) (hsv[2] * 100);
                        for (int i = 0; i < dominantColorsGen.length; i++) {
                            if ((Math.abs(hue - dominantColorsGen[i]) <= hueThreshold)
                                    && ((Math.abs(sat - dominantColorsSat[i]) <= satThreshold)
                                            && (Math.abs(val - dominantColorsVal[i]) <= valThreshold))) {
                                xVals.add(x);
                                yVals.add(y);
                                objectPixels.add(new Point(x, y));
                                inputImage.setRGB(x, y, inputImage.getRGB(x, y));
                                break;
                            }
                        }
                    }
                }

                clusteredPixelsFromMatrix = formClusters(objectPixels, 100);

                int maxCountCluster = maxCluster(clusteredPixelsFromMatrix);
                for (int i = 0; i < clusteredPixelsFromMatrix.size(); i++) {
                    if (clusteredPixelsFromMatrix.get(i).size() > (int) (0.5 * maxCountCluster)) {
                        calculateBoundingBox(clusteredPixelsFromMatrix.get(i));
                    }
                }
            }
        }

    }

    private void findObjectByV(int[] histogram) {
        int[] dominantColors = findDominantColors(histogram, 4, histogram.length);
        int VThreshold = 30; // Adjust this threshold as needed
        // int valThreshold = 20;

        for (int y = 0; y < inputImage.getHeight(); y++) {
            for (int x = 0; x < inputImage.getWidth(); x++) {
                Color pixelColor = new Color(inputImage.getRGB(x, y));
                int r = pixelColor.getRed();
                int g = pixelColor.getGreen();
                int b = pixelColor.getBlue();
                int vValue = (int) (0.615 * r - 0.515 * g - 0.100 * b);
                vValue = Math.min(255, Math.max(0, vValue + 128));
                for (int i = 0; i < dominantColors.length; i++) {
                    if (Math.abs(vValue - dominantColors[i]) <= VThreshold) {
                        // Colorize the pixel in the output image with blue
                        inputImage.setRGB(x, y, inputImage.getRGB(x, y));
                        objectPixels.add(new Point(x, y));
                        break;
                    }
                }
            }
        }
        clusteredPixelsFromMatrix = formClusters(objectPixels, 150);
        int maxCountCluster = maxCluster(clusteredPixelsFromMatrix);
        for (int i = 0; i < clusteredPixelsFromMatrix.size(); i++) {
            if (clusteredPixelsFromMatrix.get(i).size() > (int) (0.50 * maxCountCluster)) {
                calculateBoundingBox(clusteredPixelsFromMatrix.get(i));
            }
        }
    }

    public List<List<Point>> formClusters(List<Point> dataPoints, double thresholdDistance) {
        List<List<Point>> clusters = new ArrayList<>();

        for (Point dataPoint : dataPoints) {
            boolean assigned = false;

            // Try to assign the data point to an existing cluster
            for (List<Point> cluster : clusters) {
                Point centroid = calculateCentroid(cluster);
                double distance = calculateDistance(dataPoint, centroid);

                if (distance <= thresholdDistance) {
                    cluster.add(dataPoint);
                    assigned = true;
                    break;
                }
            }

            // If not assigned, create a new cluster
            if (!assigned) {
                List<Point> newCluster = new ArrayList<>();
                newCluster.add(dataPoint);
                clusters.add(newCluster);
            }
        }
        minMergeClusters(clusters, thresholdDistance);

        return clusters;
    }

    private void minMergeClusters(List<List<Point>> clusters, double thresholdDistance) {
        // Merge clusters if they are within the threshold distance
        boolean merged;
        do {
            merged = false;
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    List<Point> cluster1 = clusters.get(i);
                    List<Point> cluster2 = clusters.get(j);

                    Point centroid1 = calculateCentroid(cluster1);
                    Point centroid2 = calculateCentroid(cluster2);
                    double distance = calculateDistance(centroid1, centroid2);

                    if (distance <= 180) {
                        // Merge cluster2 into cluster1
                        cluster1.addAll(cluster2);
                        clusters.remove(j);
                        merged = true;
                        break;
                    }
                }
                if (merged) {
                    break;
                }
            }
        } while (merged);
    }

    public Point calculateCentroid(List<Point> cluster) {
        int sumX = 0;
        int sumY = 0;
        int size = cluster.size();

        for (Point point : cluster) {
            sumX += point.getX();
            sumY += point.getY();
        }

        int centroidX = sumX / size;
        int centroidY = sumY / size;

        return new Point(centroidX, centroidY);
    }

    private void findObjectByU(int[] histogram, int[] hueHistogram) {
        int[] dominantColors = findDominantColors(histogram, 4, histogram.length);
        int[] dominantColorsInHue = findDominantColors(hueHistogram, 4, hueHistogram.length);
        int VThreshold = 20;
        int hueThreshold = 5;
        for (int y = 0; y < inputImage.getHeight(); y++) {
            for (int x = 0; x < inputImage.getWidth(); x++) {
                Color pixelColor = new Color(inputImage.getRGB(x, y));
                int r = pixelColor.getRed();
                int g = pixelColor.getGreen();
                int b = pixelColor.getBlue();
                float[] hsv = new float[3];
                Color.RGBtoHSB(pixelColor.getRed(), pixelColor.getGreen(), pixelColor.getBlue(), hsv);
                int hue = (int) (hsv[0] * 360);
                int uValue = (int) (-0.147 * r - 0.289 * g + 0.436 * b);
                uValue = Math.min(255, Math.max(0, uValue + 128));
                for (int i = 0; i < dominantColors.length; i++) {
                    if ((Math.abs(uValue - dominantColors[i]) <= VThreshold)
                            && (Math.abs(hue - dominantColorsInHue[i]) <= hueThreshold)) {
                        // Colorize the pixel in the output image with blue
                        inputImage.setRGB(x, y, inputImage.getRGB(x, y));
                        objectPixels.add(new Point(x, y));
                        break;
                    }
                }
            }
        }
        clusteredPixelsFromMatrix = formClusters(objectPixels, 150);
        int maxCountCluster = maxCluster(clusteredPixelsFromMatrix);

        for (int i = 0; i < clusteredPixelsFromMatrix.size(); i++) {
            if (clusteredPixelsFromMatrix.get(i).size() > (int) (0.5 * maxCountCluster)) {
                calculateBoundingBox(clusteredPixelsFromMatrix.get(i));
            }
        }
    }

    private void readImageRGB(int width, int height, String imgPath, BufferedImage img) {
        try {
            int frameLength = width * height * 3;

            File file = new File(imgPath);
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.seek(0);

            long len = frameLength;
            byte[] bytes = new byte[(int) len];

            raf.read(bytes);

            int ind = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    byte a = 0;
                    byte r = bytes[ind];
                    byte g = bytes[ind + height * width];
                    byte b = bytes[ind + height * width * 2];

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                    // int pix = ((a << 24) + (r << 16) + (g << 8) + b);
                    img.setRGB(x, y, pix);
                    ind++;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int[] calculateHistogramU(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] histogram = new int[256];
        // Assuming 256 bins for U values

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color pixelColor = new Color(image.getRGB(x, y));
                int r = pixelColor.getRed();
                int g = pixelColor.getGreen();
                int b = pixelColor.getBlue();

                // Calculate YUV components
                int yValue = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int uValue = (int) (-0.147 * r - 0.289 * g + 0.436 * b);
                int vValue = (int) (0.615 * r - 0.515 * g - 0.100 * b);

                // Ensure YUV values are within valid range
                yValue = Math.min(255, Math.max(0, yValue));
                uValue = Math.min(255, Math.max(0, uValue + 128));
                vValue = Math.min(255, Math.max(0, vValue + 128));
                if (!(pixelColor.getGreen() == 255 && pixelColor.getRed() == 0 && pixelColor.getBlue() == 0)) {

                    histogram[uValue]++;
                }
            }
        }

        return histogram;
    }

    private int[] calculateHistogramV(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] histogram = new int[256];
        // Assuming 256 bins for U values

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color pixelColor = new Color(image.getRGB(x, y));
                int r = pixelColor.getRed();
                int g = pixelColor.getGreen();
                int b = pixelColor.getBlue();

                // Calculate V components
                int vValue = (int) (0.615 * r - 0.515 * g - 0.100 * b);

                // Ensure YUV values are within valid range
                vValue = Math.min(255, Math.max(0, vValue + 128));
                if (!(pixelColor.getGreen() == 255 && pixelColor.getRed() == 0 && pixelColor.getBlue() == 0)) {
                    histogram[vValue]++;
                }
            }
        }

        return histogram;
    }

    // Convert an image to HSV color space
    private static BufferedImage convertToHSV(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage hsvImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixelColor = new Color(image.getRGB(x, y));
                float[] hsv = new float[3];
                Color.RGBtoHSB(pixelColor.getRed(), pixelColor.getGreen(), pixelColor.getBlue(), hsv);
                int hue = (int) (hsv[0] * 360);
                int saturation = (int) (hsv[1] * 100);
                int brightness = (int) (hsv[2] * 100);
                int rgb = Color.HSBtoRGB(hue / 360f, saturation / 100f, brightness / 100f);
                hsvImage.setRGB(x, y, rgb);
            }
        }

        return hsvImage;
    }

    // Calculate the histogram of an image
    private static int[] calculateHistogram(BufferedImage image) {
        int[] histogram = new int[360];
        satHist = new int[101];
        valHist = new int[101];

        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixelColor = new Color(image.getRGB(x, y));
                float[] hsv = new float[3];
                Color.RGBtoHSB(pixelColor.getRed(), pixelColor.getGreen(), pixelColor.getBlue(), hsv);
                int hue = (int) (hsv[0] * 360);
                int sat = (int) (hsv[1] * 100);
                int val = (int) (hsv[2] * 100);
                if (!(pixelColor.getGreen() == 255 && pixelColor.getRed() == 0 && pixelColor.getBlue() == 0)) {
                    histogram[hue]++;
                    satHist[sat]++;
                    valHist[val]++;
                }
            }
        }

        return histogram;
    }

    private int[] findDominantColors(int[] histogram, int numDominantColors, int histLen) {
        // Sort the histogram indices by frequency in descending order
        Integer[] indices = new Integer[histLen];
        for (int i = 0; i < histLen; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> Integer.compare(histogram[b], histogram[a]));

        // Select the top 'numDominantColors' indices as dominant colors
        int[] dominantColors = new int[numDominantColors];
        for (int i = 0; i < numDominantColors; i++) {
            dominantColors[i] = indices[i];
        }

        return dominantColors;
    }

    public void drawBoundingBox(BufferedImage image, int x, int y, int maxX, int maxY) {
        int boxColor = Color.GREEN.getRGB(); // Color of the bounding box (red in this example)
        int cnt = 3;
        Font font = new Font("Arial", Font.BOLD, 22);
        g.setFont(font);
        g.setColor(Color.GREEN);
        g.drawString(currentObject, x + cnt + 1, maxY - cnt - 1);
        for (int j = y; j <= y + 3; j++) {
            for (int i = x; i <= maxX; i++) {
                image.setRGB(i, j, boxColor);
                if (maxY - cnt > 0) {
                    image.setRGB(i, maxY - cnt, boxColor);
                }
            }
            cnt--;
        }
        cnt = 3;
        for (int j = x; j <= x + 3; j++) {
            for (int i = y; i <= maxY; i++) {
                image.setRGB(j, i, boxColor); // Left line
                if (maxX - cnt > 0) {
                    image.setRGB(maxX - cnt, i, boxColor); // Right line
                }
            }
            cnt--;
        }

    }

    private static int maxCluster(List<List<Point>> clusters) {
        int maxCount = 0;

        for (int i = 0; i < clusters.size(); i++) {
            if (clusters.get(i).size() > maxCount) {
                maxCount = clusters.get(i).size();
            }
        }

        return maxCount;
    }

    private static int findPeakHue(int[] histogram) {
        int maxCount = 0;
        int peakHue = 0;
        for (int hue = 0; hue < histogram.length; hue++) {
            if (histogram[hue] > maxCount) {
                maxCount = histogram[hue];
                peakHue = hue;
            }
        }

        return peakHue;
    }

    public double calculateDistance(Point p1, Point p2) {
        double dx = p1.getX() - p2.getX();
        double dy = p1.getY() - p2.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public void calculateBoundingBox(List<Point> points) {
        if (points.isEmpty()) {
            // Handle the case when there are no points
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Point point : points) {
            int x = point.x;
            int y = point.y;

            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
        }

        drawBoundingBox(inputImage, minX, minY, maxX, maxY);
    }

}
