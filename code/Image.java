import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;

import javax.imageio.ImageIO;
import javax.swing.*;

public class Image {

    private int width, height;

    private final int[] image_rgb;
    private final int[] energy;
    private final int[] bw_image;
    private final int[] horizontalAccumulatedEnergy;
    private final int[] horizontal_dp_backtrack;


    private static final double[] SOBEL_X = new double[] {
      -1d, 0d, 1d,
      -2d, 0d, 2d,
      -1d, 0d, 1d };

    private static final double[] SOBEL_Y = new double[] {
      1d, 2d, 1d,
      0d, 0d, 0d,
      -1d, -2d, -1d };

    public Image(BufferedImage image) {
        width = image.getWidth();
        height = image.getHeight();
        image_rgb = extractImagedata(image);

        horizontalAccumulatedEnergy = new int[width * height];
        horizontal_dp_backtrack = new int[width * height];

        bw_image = convertToBw();
        energy = runSobelEdgeDetection(bw_image, width, height);
    }

    private static int[] runSobelEdgeDetection(int[] bw_image, int width, int height) {

        int[] energy = new int[width * height];
        int[] positions = new int[9];

        for (int k = 1; k < height - 1; k++) {
            final int height_base = k * width;
            for (int j = 1; j < width - 1; j++) {
                final int i = height_base + j;
                positions[0] = i - width - 1;
                positions[1] = i - width;
                positions[2] = i - width + 1;
                positions[3] = i - 1;
                positions[4] = i;
                positions[5] = i + 1;
                positions[6] = i + width - 1;
                positions[7] = i + width;
                positions[8] = i + width + 1;

                energy[i] = calculateEnergyValue(bw_image, positions, SOBEL_X, SOBEL_Y);
            }

        }
        paddEnergyImage(energy, width, height);

        return energy;
    }

    private static int calculateEnergyValue(int[] bw_image, int[] positions, double[] sobelX, double[] sobelY) {
        double gx = 0.0;
        double gy = 0.0;

        for (int i = 0; i < positions.length; i++) {
            gx += getlastByte(bw_image[positions[i]]) * sobelX[i];
            gy += getlastByte(bw_image[positions[i]]) * sobelY[i];
        }

        return (int) Math.sqrt(gx * gx + gy * gy);
    }

    private static double getlastByte(int val) {
        return val & 0xff;
    }


    public BufferedImage resizeHorizontally(int percent) {

        final int columsToRemove = (int) (width * (percent / 100.0));

        for (int i = 0; i < columsToRemove; i++) {
            System.out.printf("removing seam %d of %d\r", i, columsToRemove);
            int[] postitionsToRemove = findSeam();
            removeSeam(postitionsToRemove, energy, image_rgb, bw_image, width, height);
            fixEnergyImage(postitionsToRemove, bw_image, energy, width - 1, height);
            width--;
        }
        System.out.println("Done                                                ");

        return arrayToImage(image_rgb, BufferedImage.TYPE_INT_RGB);
    }

    private static void fixEnergyImage(int[] postitionsToRemove, int[] bw_image, int[] energyImg, int newWidth, int
      height) {

        for (int i = 1; i < postitionsToRemove.length - 1; i++) {

            final int posA = postitionsToRemove[i] - i;
            final int posB = posA - 1;
            final int widthMin = newWidth * i;
            final int widthMax = widthMin + newWidth - 1;

            if (posA > widthMin && posA < widthMax) {
                energyImg[posA] = calculateEnergyValue(bw_image, new int[] {
                  posA - newWidth - 1, posA - newWidth, posA - newWidth + 1,
                  posA - 1, posA, posA + 1,
                  posA + newWidth - 1, posA + newWidth, posA + newWidth + 1 }, SOBEL_X, SOBEL_Y);
            }

            if (posB > widthMin && posB < widthMax) {
                energyImg[posB] = calculateEnergyValue(bw_image, new int[] {
                  posB - newWidth - 1, posB - newWidth, posB - newWidth + 1,
                  posB - 1, posB, posB + 1,
                  posB + newWidth - 1, posB + newWidth, posB + newWidth + 1 }, SOBEL_X, SOBEL_Y);
            }
        }

        paddEnergyImage(energyImg, newWidth, height);


    }

    private static void paddEnergyImage(int[] energyImg, int width, int height) {
        for (int i = 0; i < width; i++) {
            energyImg[i] = 255;
        }

        for (int i = (width * height) - width; i < width * height; i++) {
            energyImg[i] = 255;
        }

        for (int i = width; i < width * height; i += width) {
            energyImg[i] = 255;
            energyImg[i + width - 1] = 255;
        }
    }


    static void removeSeam(int[] postitionsToRemove, int[] energy, int[] image_rgb, int[] image_bv, int width, int
      height) {

        for (int i = 0; i < postitionsToRemove.length - 1; i++) {
            int srcStart = postitionsToRemove[i] + 1;
            int dstStart = srcStart - 1 - i;
            int srcStop = postitionsToRemove[i + 1];

            shiftArray(energy, dstStart, srcStart, srcStop);
            shiftArray(image_rgb, dstStart, srcStart, srcStop);
            shiftArray(image_bv, dstStart, srcStart, srcStop);
        }

        final int dstStart = postitionsToRemove[postitionsToRemove.length - 1] - (postitionsToRemove.length - 1);
        final int srcStart = postitionsToRemove[postitionsToRemove.length - 1] + 1;
        final int srcStop = width * height;

        shiftArray(energy, dstStart, srcStart, srcStop);
        shiftArray(image_rgb, dstStart, srcStart, srcStop);
        shiftArray(image_bv, dstStart, srcStart, srcStop);
    }

    static void shiftArray(int[] array, int dstStart, int srcStart, int srcStop) {
        for (int i = 0; i < srcStop - srcStart; i++) {
            array[dstStart + i] = array[srcStart + i];
        }

    }

    private int[] findSeam() {
        rebuildAccumulatedEnergyMatrix();

        int min_energy_path = 0;
        int min_energy_value = Integer.MAX_VALUE;

        for (int i = height * width - width; i < height * width; i++) {
            if (horizontalAccumulatedEnergy[i] < min_energy_value) {
                min_energy_path = i;
                min_energy_value = horizontalAccumulatedEnergy[i];
            }
        }
        int[] min_horizontal_seam = new int[height];
        min_horizontal_seam[height - 1] = min_energy_path;

        for (int i = min_horizontal_seam.length - 1; i > 0; i--) {
            min_horizontal_seam[i - 1] = horizontal_dp_backtrack[min_horizontal_seam[i]];
        }

        min_horizontal_seam[height - 1] = min_energy_path;

        for (int i = height - 2; i > -1; i--) {
            min_horizontal_seam[i] = horizontal_dp_backtrack[min_horizontal_seam[i + 1]];
        }

        return min_horizontal_seam;
    }

    private void rebuildAccumulatedEnergyMatrix() {

        System.arraycopy(energy, 0, horizontalAccumulatedEnergy, 0, width);

        for (int i = 0; i < width; i++) {
            horizontal_dp_backtrack[i] = i;
        }

        for (int i = 1; i < height; i++) {
            final int height_base = i * width;
            for (int j = 1; j < width - 1; j++) {
                findPosOfLowestValueNeightbourInPrevRow(height_base + j, new int[] { -1, 0, 1 });
            }

            findPosOfLowestValueNeightbourInPrevRow(height_base, new int[] { 0, 1 });
            findPosOfLowestValueNeightbourInPrevRow(height_base + width - 1, new int[] { -1, 0 });
        }
    }


    private void findPosOfLowestValueNeightbourInPrevRow(int position, int[] offsets) {
        int min_energy = Integer.MAX_VALUE;
        int min_energy_previous_row = -1;
        for (int k = 0; k < offsets.length; k++) {
            int index_previous_row = position - width + offsets[k];
            if (horizontalAccumulatedEnergy[index_previous_row] < min_energy) {
                min_energy = horizontalAccumulatedEnergy[index_previous_row];
                min_energy_previous_row = index_previous_row;
            }
        }
        if (min_energy_previous_row < 0) {
            throw new IllegalStateException("could not find previous neighbour");
        }

        horizontalAccumulatedEnergy[position] = energy[position] + min_energy;
        horizontal_dp_backtrack[position] = min_energy_previous_row;
    }

    public int[] convertToBw() {

        int[] bw = new int[width * height];

        for (int i = 0; i < width * height; i++) {
            final int
              b = (image_rgb[i] & 0xff),
              g = (image_rgb[i] >> 8) & 0xff,
              r = ((image_rgb[i] >> 16) & 0xff);

            int gray = (int) (r * 0.2989 + g * 0.5870 + b * 0.1140);
            bw[i] = gray << 16 | gray << 8 | gray;
        }
        return bw;
    }


    private static int[] extractImagedata(BufferedImage image) {

        final byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        final int width = image.getWidth();
        final int height = image.getHeight();
        final boolean hasAlphaChannel = image.getAlphaRaster() != null;
        final int pixelLength = hasAlphaChannel ? 4 : 3;
        final int pixelOffset = hasAlphaChannel ? 1 : 0;

        int[] result = new int[width * height];
        for (int pixel = 0, idx = 0; pixel < pixels.length; pixel += pixelLength, idx++) {
            int argb = -0xff000000 |
              (pixels[pixel + pixelOffset + 0] & 0xff) << 0 | // Blue
              (pixels[pixel + pixelOffset + 1] & 0xff) << 8 | // Green
              (pixels[pixel + pixelOffset + 2] & 0xff) << 16; // Red

            result[idx] = argb;
        }

        return result;
    }


    private BufferedImage arrayToImage(int[] image, int bufferedimagetype) {
        BufferedImage output = new BufferedImage(width, height, bufferedimagetype);
        output.setRGB(0, 0, width, height, image, 0, width);
        return output;
    }


    public static void writeImage(BufferedImage image, Path path) throws IOException {
        ImageIO.write(image, "jpg", Files.newOutputStream(path));
        System.out.println("image written to " + path.toString());
    }

    public static void displayImage(BufferedImage image) {
        JFrame frame = new JFrame();
        frame.getContentPane().setLayout(new FlowLayout());
        frame.getContentPane().add(new JLabel(new ImageIcon(image)));
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void displayColorImage(int[] image, int bufferedimagetype) {
        displayImage(arrayToImage(image, bufferedimagetype));
    }

    private void displayColorImage() {

        int[] ints = new int[width * height];

        System.arraycopy(image_rgb, 0, ints, 0, width * height);

        displayColorImage(ints, BufferedImage.TYPE_INT_RGB);
    }


    private int[] normalizeEnergy() {
        int[] normalized = new int[width * height];

        for (int i = 0; i < width * height; i++) {
            int normalizedValue = Math.min(255, energy[i]) & 0xff;
            normalized[i] = normalizedValue << 16 | normalizedValue << 8 | normalizedValue;
        }

        return normalized;
    }

    public BufferedImage highlightNSeams(int n) {
        int[] highlighImage = new int[width * height];
        System.arraycopy(image_rgb, 0, highlighImage, 0, width * height);
        rebuildAccumulatedEnergyMatrix();

        int topSeamStart[][] = new int[width][2];

        for (int i = 0; i < width; i++) {
            int arrayPos = width * (height - 1) + i;
            topSeamStart[i] = new int[] { arrayPos, horizontalAccumulatedEnergy[arrayPos] };
        }

        Arrays.sort(topSeamStart, Comparator.comparingInt(o -> o[1]));

        for (int i = 0; i < Math.min(n, width); i++) {

            int pos = topSeamStart[i][0];

            for (int j = 0; j < height; j++) {
                highlighImage[pos] = 0xff << 16;
                pos = horizontal_dp_backtrack[pos];
            }
        }

        return arrayToImage(highlighImage, BufferedImage.TYPE_INT_RGB);
    }


    private static JLabel toJlabel(BufferedImage br) {
        return new JLabel(new ImageIcon(br));
    }

    public static void main(String[] args) throws IOException {

        if (args.length < 2) {
            System.out.println("image resize% [-d debug or -dd debug to disk]");
            return;
        }

        String filename = args[0];
        String filenameWithoutFileEnding = filename.substring(0, filename.lastIndexOf("."));

        int resizePercent = Integer.parseInt(args[1]);
        boolean debug = args.length > 2 && args[2].equals("-d");
        boolean debugToDisk = args.length > 2 && args[2].equals("-dd");

        BufferedImage image = ImageIO.read(new File(filename));

        Image image1 = new Image(image);
        BufferedImage resizedImage = image1.resizeHorizontally(resizePercent);

        Image.writeImage(resizedImage,
          Paths.get("resized.jpg"));

        if (debug) {
            JFrame frame = new JFrame();
            frame.getContentPane().setLayout(new FlowLayout());

            frame.getContentPane().add(toJlabel(resizedImage));

            frame.getContentPane().add(toJlabel(image1.arrayToImage(image1.normalizeEnergy(),
              BufferedImage.TYPE_BYTE_GRAY)));

            frame.getContentPane().add(toJlabel(image1.highlightNSeams(50)));

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        } else if (debugToDisk) {

            Image.writeImage(image1.arrayToImage(image1.normalizeEnergy(), BufferedImage.TYPE_BYTE_GRAY),
              Paths.get("energy.jpg"));

            Image.writeImage(image1.highlightNSeams(50),
              Paths.get("50seams.jpg"));
        }
    }
}