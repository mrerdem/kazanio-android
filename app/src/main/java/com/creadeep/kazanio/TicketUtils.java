package com.creadeep.kazanio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static com.creadeep.kazanio.MainActivity.mTextRecognizer;

public class TicketUtils {
    private String resultNumber = null;
    private String resultNumberSingleRow = null;
    private String resultDate = null;
    private String resultFraction = null;

    private Bitmap mDateBitmap; // Image with only cropped date area
    private Bitmap mFractionBitmap; // Image with only cropped fraction area (ceyrek, yarim, vs.)
    private Bitmap mNumberNoGapBitmap; // Image of number area without gaps between numbers
    private Bitmap allRowsBitmap;
    private Bitmap tmp;
    private Bitmap tmp2;
    private RectF location; // This will be used for cropping operations
    private Matrix matrix = new Matrix(); // This will be used for cropping operations
    private Canvas canvas;

    private int SHRINK_CROP_6 = 4; // Defines how much of each 2 digit number area will be cropped [px] (for rows with 6 2-digit numbers)
    private int SHRINK_SHIFT_6 = 8; // Defines how much the cropping locations will be shifted horizontally [px] (for rows with 6 2-digit numbers)
    private int SHRINK_CROP_6_2 = 5; // Defines how much of each 2 digit number area will be cropped [px] (for classic milli piyango)
    private int SHRINK_SHIFT_6_2 = 5; // Defines how much the cropping locations will be shifted horizontally [px] (for classic milli piyango)
    private int VERTICAL_TOLERANCE = 8; // Defines top and bottom edge tolerances for cropping

    private int[][] verticalShift = {{16, 0, 0, 0, 0, 0, 0, 0}, {14, 0, 0, 0, 0, 0, 0, 0}, {39, 24, 9, 0, 0, 0, 0, 0}, {38, 15, 0, 0, 0, 0, 0, 0}, {41, 17, 0, 0, 0, 0, 0, 0}}; // The vertical shift amount to eliminate vertical shift to provide vertical centering of viewfinder overlays. (offset from standard top 50px margin)

    private List<Integer> cropLocs6 = new ArrayList<>(); // Locations for cropping to generate shrank image with spaces between 2-digit numbers are removed (for rows with 6 2-digit numbers)
    private List<Integer> cropLocs6_2 = new ArrayList<>(); // Locations for cropping to generate shrank image with spaces between 2-digit numbers are removed (for classical Milli Piyango ticket)
    private List<Integer> cropLocs10 = new ArrayList<>(); // Locations for cropping to generate shrank image with spaces between 2-digit numbers are removed (for rows with 10 2-digit numbers)
    List<Integer> shiftedCropLocs; // Locations for cropping with a left or right shift to diminish user's horizontal misalignment

    public TicketUtils(Context c) {
        // Fill crop locations
        cropLocs6.add(38); // Mid-point between the 1st and 2nd 2-digit numbers
        cropLocs6.add(75);
        cropLocs6.add(113);
        cropLocs6.add(151);
        cropLocs6.add(189);
        cropLocs6_2.add(55); // Mid-point between the 1st and 2nd 2-digit numbers
        cropLocs6_2.add(88);
        cropLocs6_2.add(121);
        cropLocs6_2.add(154);
        cropLocs6_2.add(187);
        cropLocs6_2.add(219);
        cropLocs10.add(155); // Mid-point between the 1st and 2nd 2-digit numbers
        cropLocs10.add(177);
        cropLocs10.add(200);
        cropLocs10.add(222);
        cropLocs10.add(245);
        cropLocs10.add(267);
        cropLocs10.add(290);
        cropLocs10.add(312);
        cropLocs10.add(335);
        matrix.postRotate(90);
    }

    public Ticket extractData(Context c, Bitmap ticketImage, int type, String[] poi) {

        int rows = poi.length - 2; // number of number rows = all rows - date - fraction

        // Image with only cropped number area
        Bitmap mNumberBitmap;
        if (type == 1) { // Milli Piyango
            if (rows == 1) { // New ticket type (terminal print)
                // Fraction
                if (poi[0].contains("1")) {// if fraction is a poi (i.e. it is not detected yet)
                    location = new RectF(180, 180, 240, 400);
                    mFractionBitmap = ImageUtils.cropImageUntilEnd(ticketImage, location, 0); // Crop number area
                    resultFraction = ImageUtils.recognizeText(mTextRecognizer, c, mFractionBitmap); // Extract the text
                    resultFraction = com.creadeep.kazanio.TextUtils.extractDigits(resultFraction); // Extract only the digits
                    if (!resultFraction.equals("11") && !resultFraction.equals("12") && !resultFraction.equals("14"))
                        resultFraction = null;
                }

                // Number
                // Crop & extract digits
                if (poi[1].contains("1")) { // if number is a poi (i.e. it is not detected yet)
                    location = new RectF(268, 120, 314, 360);
                    mNumberBitmap = ImageUtils.cropImageUntilEnd(ticketImage, location, 0); // Crop number area
                    resultNumber = ImageUtils.recognizeText(mTextRecognizer, c, mNumberBitmap);
                    resultNumber = com.creadeep.kazanio.TextUtils.extractDigits(resultNumber);
                    if (resultNumber.length() < 6 || resultNumber.length() > 7)
                        resultNumber = null;
                }

                // Date
                // Crop & extract digits
                if (poi[2].contains("1")) { // if date is a poi (i.e. it is not detected yet)
                    location = new RectF(314, 150, 344, 360);
                    mDateBitmap = ImageUtils.cropImageUntilEnd(ticketImage, location, 0); // Crop number area
                    resultDate = ImageUtils.recognizeText(mTextRecognizer, c, mDateBitmap); // Extract the text
                    resultDate = com.creadeep.kazanio.TextUtils.extractDigits(resultDate); // Extract only the digits
                    // All digits are extracted correctly
                    if (resultDate.length() != 8 || isDateInvalid(resultDate, type))
                        resultDate = null;
                }
            }
            else if (rows == 2){
                // Fraction
                if (poi[0].contains("1")) {// if fraction is a poi (i.e. it is not detected yet)
                    location = new RectF(242, 0, 265, 480);
                    mFractionBitmap = ImageUtils.cropImage(ticketImage, location, 0, matrix); // Crop number area
                    resultFraction = ImageUtils.recognizeText(mTextRecognizer, c, mFractionBitmap); // Extract the text
                    resultFraction = com.creadeep.kazanio.TextUtils.extractDigits(resultFraction); // Extract only the digits
                    if (!resultFraction.equals("11") && !resultFraction.equals("12") && !resultFraction.equals("14"))
                        resultFraction = null;
                }

                // Number
                // Crop & extract digits
                if (poi[1].contains("1")) { // if number is a poi (i.e. it is not detected yet)

                    location = new RectF(295, 50, 348, 310);
                    mNumberBitmap = ImageUtils.cropImage(ticketImage, location, 0, matrix); // Crop number area

                    // Prepare shrank image to remove gaps
                    tmp = Bitmap.createBitmap(mNumberBitmap.getWidth(), mNumberBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    canvas = new Canvas(tmp);
                    shiftedCropLocs = new ArrayList<>(cropLocs6_2); // List to store the shifted center position of removed white areas between 2-digit numbers (1st pass: no shift, 2nd pass: shift left, 3rd pass: shift right)
                    shiftedCropLocs.addAll(cropLocs6_2);

                    int shiftedTrials = 0; // Try 3 times, 1st: center, 2nd: left shifted, 3rd: right shifted
                    while (shiftedTrials < 3) { // Try shrinking number row by without shifting and shifting horizontally in both directions
                        // Set shifted number middle locations
                        if (shiftedTrials != 0) {
                            for (int j = 0; j < 6; j++) { // 6 is to remove the letters to the right of the last digit
                                shiftedCropLocs.set(j, cropLocs6_2.get(j) + SHRINK_SHIFT_6_2 * (1 - (shiftedTrials % 2) * 2));
                            }
                        }
                        // Shrink image removing gaps between two digit numbers (First and last are processed separately to prevent extreme cropping on left/right when horizontal alignment is not perfect
                        int currentWidth = 0;
                        // First number
                        location = new RectF(0, 0, Math.max(shiftedCropLocs.get(0) - SHRINK_CROP_6_2, 0), mNumberBitmap.getHeight());
                        mNumberNoGapBitmap = ImageUtils.cropImage(mNumberBitmap, location, 0, null);
                        canvas.drawBitmap(mNumberNoGapBitmap, currentWidth, 0, null);
                        currentWidth = shiftedCropLocs.get(0) - SHRINK_CROP_6_2 - 1; // -1 is to fix black vertical line, fix it if you want
                        // Remaining numbers
                        for (int j = 1; j < 6; j++) {
                            location = new RectF(shiftedCropLocs.get(j - 1) + SHRINK_CROP_6_2, 0, Math.max(shiftedCropLocs.get(j) - SHRINK_CROP_6_2, shiftedCropLocs.get(j - 1) + SHRINK_CROP_6_2 + 1), mNumberBitmap.getHeight());
                            mNumberNoGapBitmap = ImageUtils.cropImage(mNumberBitmap, location, 0, null);
                            canvas.drawBitmap(mNumberNoGapBitmap, currentWidth + 1, 0, null);
                            currentWidth += shiftedCropLocs.get(j) - shiftedCropLocs.get(j - 1) - 2 * SHRINK_CROP_6_2;
                        }
                        // Last number
                        location = new RectF(Math.min(shiftedCropLocs.get(5) + SHRINK_CROP_6_2, mNumberBitmap.getWidth() - 1), 0, mNumberBitmap.getWidth(), mNumberBitmap.getHeight());
                        mNumberNoGapBitmap = ImageUtils.cropImage(mNumberBitmap, location, 0, null);
                        canvas.drawBitmap(mNumberNoGapBitmap, currentWidth + 1, 0, null);

                        resultNumber = ImageUtils.recognizeText(mTextRecognizer, c, tmp);
                        resultNumber = com.creadeep.kazanio.TextUtils.extractDigits(resultNumber);
                        if ((resultNumber != null && !resultNumber.equals("")) && rows == 2 && resultNumber.length() == 6 || rows == 3 && resultNumber.length() == 7) // Do not try remaining shifted shrank images if already found
                            break;
                        shiftedTrials++;
                    }

                    if ((resultNumber != null && !resultNumber.equals("")) && rows == 2 && resultNumber.length() != 6) // Classical
                        resultNumber = null;
                    if ((resultNumber != null && !resultNumber.equals("")) && rows == 3 && resultNumber.length() != 7) // New Year's Eve
                        resultNumber = null;
                }

                // Date
                // Crop & extract digits
                if (poi[2].contains("1")) { // if date is a poi (i.e. it is not detected yet)
                    location = new RectF(260, 0, /*307*/295, 480);
                    mDateBitmap = ImageUtils.cropImageUntilEnd(ticketImage, location, 0); // Crop number area
                    resultDate = ImageUtils.recognizeText(mTextRecognizer, c, mDateBitmap); // Extract the text
                    String resultDateYearAndDay = com.creadeep.kazanio.TextUtils.extractDigits(resultDate); // Extract only the digits
                    String resultDateMonth = com.creadeep.kazanio.TextUtils.removeDigits(resultDate); // Extract only the digits

                    if (resultDateYearAndDay.length() == 6) {
                        switch (resultDateMonth) {
                            case "OCAK":
                                resultDate = resultDateYearAndDay.substring(4) + "01" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "ŞUBAT":
                            case "SUBAT":
                                resultDate = resultDateYearAndDay.substring(4) + "02" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "MART":
                                resultDate = resultDateYearAndDay.substring(4) + "03" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "NİSAN":
                            case "NISAN":
                                resultDate = resultDateYearAndDay.substring(4) + "04" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "MAYIS":
                                resultDate = resultDateYearAndDay.substring(4) + "05" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "HAZİRAN":
                            case "HAZIRAN":
                                resultDate = resultDateYearAndDay.substring(4) + "06" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "TEMMUZ":
                                resultDate = resultDateYearAndDay.substring(4) + "07" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "AĞUSTOS":
                            case "AGUSTOS":
                                resultDate = resultDateYearAndDay.substring(4) + "08" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "EYLÜL":
                            case "EYLUL":
                                resultDate = resultDateYearAndDay.substring(4) + "09" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "EKİM":
                            case "EKIM":
                                resultDate = resultDateYearAndDay.substring(4) + "10" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "KASIM":
                                resultDate = resultDateYearAndDay.substring(4) + "11" + resultDateYearAndDay.substring(0, 4);
                                break;
                            case "ARALIK":
                                resultDate = resultDateYearAndDay.substring(4) + "12" + resultDateYearAndDay.substring(0, 4);
                                break;
                            default:
                                resultDate = null;
                                break;
                        }
                        // All digits are extracted correctly
                        if (resultDate != null && (resultDate.length() != 8 || isDateInvalid(resultDate, type)))
                            resultDate = null;
                    }
                    else{
                        resultDate = null;
                    }
                }
            }
            return new Ticket(type, rows, resultNumber, resultDate, resultFraction, null, 0, 0, 0);
        }
        else if (type == 2 || type == 3) { // Sayisal Loto & Super Loto
            // Number
            // Crop number area
            if (type == 2) { // Sayisal Loto
                location = new RectF(249 + verticalShift[type - 1][rows-1] - VERTICAL_TOLERANCE, // Vertical start point (top) of the numbers area 199 + 50
                        129, // Horizontal start point (left) of the 6 2-digit numbers
                        249 + verticalShift[type - 1][rows-1] + 30 * rows + VERTICAL_TOLERANCE, // Vertical start point (top) of the numbers area 199 + 50
                        351); // Horizontal end point (right) of the 6 2-digit numbers
            }
            else { // Super Loto
                location = new RectF(201 + verticalShift[type - 1][rows-1] - VERTICAL_TOLERANCE, // Vertical start point (top) of the numbers area 151 + 50
                        129, // Horizontal start point (left) of the 6 2-digit numbers
                        201 + verticalShift[type - 1][rows-1] + 30 * rows + VERTICAL_TOLERANCE, // Vertical start point (top) of the numbers area 151 + 50
                        351); // Horizontal end point (right) of the 6 2-digit numbers
            }
            allRowsBitmap = ImageUtils.cropImage(ticketImage, location, 0, matrix); // All the rows
            double rowHeight = (allRowsBitmap.getHeight() - 2 * VERTICAL_TOLERANCE) / rows;

            // OCR entire number area
            tmp2 = Bitmap.createScaledBitmap(allRowsBitmap, allRowsBitmap.getWidth() * 2, allRowsBitmap.getHeight(), false); // widen the image for better ocr results
            resultNumberSingleRow = ImageUtils.recognizeText(mTextRecognizer, c, tmp2);
            resultNumberSingleRow = TextUtils.extractDigits(resultNumberSingleRow);
            StringBuilder sb = new StringBuilder();
            if (resultNumberSingleRow.length() == rows * 12) {
                String[] columns = new String[6];
                for (int i = 0; i < 6; i++) {
                    columns [i] = resultNumberSingleRow.substring(i * 2 * rows, (i + 1) * 2 * rows);
                }
                for (int i = 0; i < rows; i++) {
                    sb.append(columns[0].substring(i * 2, i * 2 + 2));
                    sb.append(columns[1].substring(i * 2, i * 2 + 2));
                    sb.append(columns[2].substring(i * 2, i * 2 + 2));
                    sb.append(columns[3].substring(i * 2, i * 2 + 2));
                    sb.append(columns[4].substring(i * 2, i * 2 + 2));
                    sb.append(columns[5].substring(i * 2, i * 2 + 2));
                }
            }
            else {
                for (int i = 0; i < rows; i++) {
                    sb.append("XXXXXXXXXXXX");
                }
            }

            resultNumber = sb.toString();

            // Date
            // Crop & extract digits
            if (poi[rows + 1].contains("1")) { // if date is of interest
                if (type == 2) // Sayisal Loto
                    location = new RectF(249 + verticalShift[type - 1][rows-1] + 30 * rows, 150, 279 + verticalShift[type - 1][rows-1] + 30 * rows, 360); // 249 = 199 + 50 TODO: Make this more precise with requirement of + 10 like Super Loto below
                else // Super Loto
                    location = new RectF(211 + verticalShift[type - 1][rows-1] + 30 * rows, 150, 241 + verticalShift[type - 1][rows-1] + 30 * rows, 360); // 211 = 151 + 60 (+10 is because of extra gap requirement between number area and the date)
                mDateBitmap = ImageUtils.cropImageUntilEnd(ticketImage, location, 0); // Crop number area

                tmp2 = Bitmap.createScaledBitmap(mDateBitmap, mDateBitmap.getWidth() * 2, mDateBitmap.getHeight() * 2, false); // widen the image for better ocr results
                resultDate = ImageUtils.recognizeText(mTextRecognizer, c, tmp2); // Extract the text
                resultDate = com.creadeep.kazanio.TextUtils.extractDigits(resultDate); // Extract only the digits
                // All digits are extracted correctly
                if (resultDate.length() != 8 || isDateInvalid(resultDate, type))
                    resultDate = null;
            }

            return new Ticket(type, 0, resultNumber, resultDate, String.valueOf(rows), null, 0, 0, 0);
        }

        else if(type == 4){ // On Numara
            // Number
            // Crop & extract digits
            // Crop number area
            location = new RectF(197 + verticalShift[type - 1][rows-1] /*- VERTICAL_TOLERANCE*/, // 197 = 147 + 50
                    0,
                    197 + verticalShift[type - 1][rows-1] + 47 * rows /*+ VERTICAL_TOLERANCE*/,
                    480);
            allRowsBitmap = ImageUtils.cropImage(ticketImage, location, 0, matrix); // All the rows
            double rowHeight = (allRowsBitmap.getHeight() /*- 2 * VERTICAL_TOLERANCE*/) / rows;

            // OCR entire number area
            tmp2 = Bitmap.createScaledBitmap(allRowsBitmap, allRowsBitmap.getWidth() * 2, allRowsBitmap.getHeight(), false); // widen the image for better ocr results
            resultNumberSingleRow = ImageUtils.recognizeText(mTextRecognizer, c, tmp2);
            resultNumberSingleRow = TextUtils.extractDigits(resultNumberSingleRow);
            StringBuilder sb = new StringBuilder();
            if (resultNumberSingleRow.length() == rows * 20) {
                sb.append(resultNumberSingleRow);
            }
            else {
                for (int i = 0; i < rows; i++) {
                    sb.append("XXXXXXXXXXXX");
                }
            }
            resultNumber = sb.toString();

            // Date
            // Crop & extract digits
            if(poi[rows+1].contains("1")) { // if date is of interest
                location = new RectF(187 + verticalShift[type - 1][rows-1] + 47 * rows, 150, 217 + verticalShift[type - 1][rows-1] + 47 * rows, 360); // 187 = 147 + 50 - 10 (due to row end being close to date), 217 = 197 + 30 - 10
                mDateBitmap = ImageUtils.cropImageUntilEnd(ticketImage, location, 0); // Crop number area

                tmp2 = Bitmap.createScaledBitmap(mDateBitmap, mDateBitmap.getWidth() * 2, mDateBitmap.getHeight() * 2, false); // widen the image for better ocr results
                resultDate = ImageUtils.recognizeText(mTextRecognizer, c, tmp2); // Extract the text
                resultDate = com.creadeep.kazanio.TextUtils.extractDigits(resultDate); // Extract only the digits
                // All digits are extracted correctly
                if (resultDate.length() != 8 || isDateInvalid(resultDate, type))
                    resultDate = null;
            }

            return new Ticket(type, 0, resultNumber, resultDate, String.valueOf(rows), null, 0, 0, 0);
        }

        else { // Sans Topu
            // Number
            // Crop & extract digits
            // Crop number area
            location = new RectF(188 + verticalShift[type - 1][rows-1] - VERTICAL_TOLERANCE, // 188 = 138 + 50
                    0,
                    188 + verticalShift[type - 1][rows-1] + 47 * rows + VERTICAL_TOLERANCE,
                    351);
            allRowsBitmap = ImageUtils.cropImage(ticketImage, location, 0, matrix); // All the rows
            double rowHeight = (allRowsBitmap.getHeight() - 2 * VERTICAL_TOLERANCE) / rows;

            // Process each row
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rows; i++){
                if(poi[i + 1].contains("1")) { // if the row contains point of interest
                    location = new RectF(0, Math.round(Math.max(0, rowHeight * i)), allRowsBitmap.getWidth(), Math.round(Math.min(rowHeight * (i + 1) + 2 * VERTICAL_TOLERANCE, allRowsBitmap.getHeight())));
                    mNumberBitmap = ImageUtils.cropImage(allRowsBitmap, location, 0, null);

                    // Prepare shrinked image to remove gaps
                    tmp = Bitmap.createBitmap(mNumberBitmap.getWidth(), mNumberBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    canvas = new Canvas(tmp);
                    shiftedCropLocs = new ArrayList<>(cropLocs6); // List to store the shifted center position of removed white areas between 2-digit numbers (1st pass: no shift, 2nd pass: shift left, 3rd pass: shift right)
                    shiftedCropLocs.set(4, shiftedCropLocs.get(4) + 7); // 1.6mm = 7px shift

                    int shiftedTrials = 0;
                    while (shiftedTrials < 3) { // Try shrinking number row by without shifting and shifting horizontally in both directions
                        // Set shifted number middle locations
                        if (shiftedTrials != 0) {
                            for (int j = 0; j < 5; j++) {
                                if (j == 4) // Shift last mid +7px to remove + sign before the last number
                                    shiftedCropLocs.set(j, cropLocs6.get(j) + 7 + SHRINK_SHIFT_6 * (1 - (shiftedTrials % 2) * 2));
                                else
                                    shiftedCropLocs.set(j, cropLocs6.get(j) + SHRINK_SHIFT_6 * (1 - (shiftedTrials % 2) * 2));
                            }
                        }
                        // Shrink image removing gaps between two digit numbers (First and last are processed separately to prevent extreme cropping on left/right when horizontal alignment is not perfect
                        int currentWidth = 0;
                        // First number
                        location = new RectF(0, 0, Math.max(shiftedCropLocs.get(0) - SHRINK_CROP_6, 0), mNumberBitmap.getHeight());
                        mNumberNoGapBitmap = ImageUtils.cropImage(mNumberBitmap, location, 0, null);
                        canvas.drawBitmap(mNumberNoGapBitmap, currentWidth, 0, null);
                        currentWidth += shiftedCropLocs.get(0) - SHRINK_CROP_6 - 1; // -1 is to fix black vertical line, fix it if you want
                        // Remaining numbers
                        for (int j = 1; j < shiftedCropLocs.size(); j++) {
                            location = new RectF(shiftedCropLocs.get(j - 1) + SHRINK_CROP_6, 0, Math.max(shiftedCropLocs.get(j) - SHRINK_CROP_6, shiftedCropLocs.get(j - 1) + SHRINK_CROP_6 + 1), mNumberBitmap.getHeight());
                            mNumberNoGapBitmap = ImageUtils.cropImage(mNumberBitmap, location, 0, null);
                            canvas.drawBitmap(mNumberNoGapBitmap, currentWidth + 1, 0, null);
                            currentWidth += shiftedCropLocs.get(j) - shiftedCropLocs.get(j - 1) - 2 * SHRINK_CROP_6;

                        }
                        // Last number
                        location = new RectF(Math.min(shiftedCropLocs.get(shiftedCropLocs.size() - 1) + SHRINK_CROP_6 + 14, mNumberBitmap.getWidth() - 1), 0, mNumberBitmap.getWidth(), mNumberBitmap.getHeight()); // +14 is to remove + sign before the last number
                        mNumberNoGapBitmap = ImageUtils.cropImage(mNumberBitmap, location, 0, null);
                        canvas.drawBitmap(mNumberNoGapBitmap, currentWidth + 1, 0, null);

                        tmp2 = Bitmap.createScaledBitmap(tmp, tmp.getWidth() * 2, tmp.getHeight(), false); // widen the image for better ocr results
                        resultNumberSingleRow = ImageUtils.recognizeText(mTextRecognizer, c, tmp2);
                        resultNumberSingleRow = com.creadeep.kazanio.TextUtils.extractDigits(resultNumberSingleRow);
                        if (resultNumberSingleRow.length() == 12)
                            break;
                        shiftedTrials++;
                    }
                    if (resultNumberSingleRow.length() == 12) {
                        sb.append(resultNumberSingleRow);
                    }
                    else { // if the result has not enough digits, fill it with Xs
                        sb.append("XXXXXXXXXXXX");
                    }
                }
                else { // if the row is not of interest
                    sb.append("XXXXXXXXXXXX");
                }
            }
            resultNumber = sb.toString();

            // Date
            // Crop & extract digits
            if (poi[rows + 1].contains("1")) { // if date is of interest
                location = new RectF(188 + verticalShift[type - 1][rows-1] + 47 * rows, 150, 218 + verticalShift[type - 1][rows-1] + 47 * rows, 360); // 218 = 188 + 30
                mDateBitmap = ImageUtils.cropImageUntilEnd(ticketImage, location, 0); // Crop number area

                tmp2 = Bitmap.createScaledBitmap(mDateBitmap, mDateBitmap.getWidth() * 2, mDateBitmap.getHeight() * 2, false); // widen the image for better ocr results
                resultDate = ImageUtils.recognizeText(mTextRecognizer, c, tmp2); // Extract the text
                if(resultDate.length()>14)
                    resultDate = resultDate.substring(0,14); // Remove long part in case it reads the next line as well (cekilis sayisi...)
                resultDate = com.creadeep.kazanio.TextUtils.extractDigits(resultDate); // Extract only the digits
                // All digits are extracted correctly
                if (resultDate.length() != 8 || isDateInvalid(resultDate, type))
                    resultDate = null;
            }

            return new Ticket(type, 0, resultNumber, resultDate, String.valueOf(rows), null, 0, 0, 0);
        }
    }

    /**
     * Checks if a given date is invalid for a given lottery type
     * @param dateToCheck Date to be checked (DDMMYYYY)
     * @param gameType Lottery type (1: MP, 2: SL, ...)
     * @return True: invalid, False: valid
     */
    public static boolean isDateInvalid(String dateToCheck, int gameType){
        Calendar c = Calendar.getInstance();

        // Check for year
        if(dateToCheck.length() == 8) {
            // General range check (year > 2018, month:1-12, day:1-31)
            int year = Integer.parseInt(dateToCheck.substring(4, 8));
            if (year > 2017 && year <= c.get(Calendar.YEAR)) { // Online results are available starting from 2018
                // Check for month
                int month = Integer.parseInt(dateToCheck.substring(2, 4));
                if (month > 0 && month < 13) {
                    // Check for day
                    int day = Integer.parseInt(dateToCheck.substring(0, 2));
                    if (((month == 1 || month == 3 || month == 5 || month == 7 || month == 8 || month == 10 || month == 12 ) && day > 0 && day < 32) ||
                            ((month == 4 || month == 6 || month == 9 || month == 11) && day > 0 && day < 31) ||
                            (year % 4 == 0 && month == 2 && day > 0 && day < 30) ||
                            (year % 4 != 0 && month == 2 && day > 0 && day < 29)){
                        // Game specific check
                        if (gameType == 6 || gameType == 7 || gameType == 10) // Super Piyango, Banko Piyango, Super On Numara (daily draws)
                            return false;
                        if (gameType == 1) // Milli Piyango
                            return !(((month == 1 || month == 3 || month == 5 || month == 6 || month == 7 || month == 9 || month == 10 || month == 11) && (day == 9 || day == 19 || day == 29)) || // On 9th, 19th and 29th in Jan, Mar, May, Jun, Jul, Sep, Oct, Nov.
                                    (year % 4 == 0 && month == 2 && (day == 9 || day == 19 || day == 29)) || // On 9th, 19th and 29th in February with 29 days
                                    (year % 4 != 0 && month == 2 && (day == 9 || day == 19 || day == 28)) || // On 9th, 19th and 28th in February with 28 days
                                    (month == 4 && (day == 9 || day == 23)) || // On 9th and 23rd in April
                                    (month == 8 && (day == 9 || day == 19 || day == 30)) || // On 9th, 19th and 30th in August
                                    (month == 12 && day == 31)); // On 31st in Dec
                        else if (gameType == 2 || gameType == 8) { // Sayisal Loto, Super Sayisal Loto (Her Carsamba & Cumartesi)
                            Calendar calendar = Calendar.getInstance();
                            calendar.set(year, month - 1, day);
                            return !(calendar.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY);
                        }
                        else if (gameType == 3 || gameType == 9) { // Super Loto (Her Persembe)
                            Calendar calendar = Calendar.getInstance();
                            calendar.set(year, month - 1, day);
                            return !(calendar.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY);
                        }
                        else if (gameType == 4) { // On Numara (Her Pazartesi)
                            Calendar calendar = Calendar.getInstance();
                            calendar.set(year, month - 1, day);
                            return !(calendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY);
                        }
                        else if (gameType == 5 || gameType == 11) { // Sans Topu (Her Carsamba)
                            Calendar calendar = Calendar.getInstance();
                            calendar.set(year, month - 1, day);
                            return !(calendar.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY);
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Converts DDMMYYYY to DD.MM.YYYY
     * @param unformattedDate DDMMYYYY
     * @return Formatted date (DD.MM.YYYY)
     */
    public static String getFormattedDate(String unformattedDate){
        if(unformattedDate.length() > 7)
            return unformattedDate.substring(0, 2) + "." + unformattedDate.substring(2, 4) + "." + unformattedDate.substring(4, 8);
        else
            return null;
    }

    /**
     * Converts DD.MM.YYYY to DDMMYYYY
     * @param formattedDate DD.MM.YYYY
     * @return Unformatted date (DDMMYYYY)
     */
    public static String getUnformattedDate(String formattedDate){
        if(formattedDate.length() > 9)
            return formattedDate.substring(0,2) + formattedDate.substring(3,5) + formattedDate.substring(6,10);
        else
            return null;
    }

    /**
     * Returns the ticket cost for all ticket types and fractions
     * @param gameType (int) 1:Milli Piyango, 2:Sayisal Loto etc.
     * @param fraction (String) 11: Tam, 12: Yarim, 1: Tek kolon, etc.
     * @param date (String) DDMMYYYY
     * @return double cost
     */
    public static double findTicketCost(int gameType, String fraction, String date){
        if (gameType == 1) { // Milli Piyango
            if (date.substring(0, 4).equals("3112")) { // Special New Year's price
                switch (fraction) {
                    case "11":
                        return 80.00;
                    case "12":
                        return 40.00;
                    case "14":
                        return 20.00;
                }
            }
            else { // Regular price
                double fullPrice = 30.00;
                // Check date range
                if (Integer.parseInt(date.substring(4, 8) + date.substring(2, 4) + date.substring(0, 2)) < 20200209) // 09 Feb 2020: 24 -> 30 lira
                    fullPrice = 24.00;
                // Consider fraction
                switch (fraction) {
                    case "11":
                        return fullPrice;
                    case "12":
                        return fullPrice / 2;
                    case "14":
                        return fullPrice / 4;
                }
            }
        }
        else if (gameType == 2 || gameType == 8) { // Sayisal Loto, Super Sayisal Loto
            return Integer.parseInt(fraction) * 1.50;
        }
        else if (gameType == 3 || gameType == 9) { // Super Loto
            return Integer.parseInt(fraction) * 2.00;
        }
        else if (gameType == 4 || gameType == 10) { // On Numara
            return Integer.parseInt(fraction) * 1.00;
        }
        else if (gameType == 5 || gameType == 11) { // Sans Topu
            return Integer.parseInt(fraction) * 1.00;
        }
        else if (gameType == 6) { // Super Piyango
            return 3.00;
        }
        else if (gameType == 7) { // Banko Piyango
            return 2.00;
        }
        return 0f;
    }

    /**
     * Used to order the 2-digit numbers within a row, in case ocr mixes their order.
     * @param number Input string with n many 2-digit numbers
     * @return String with ordered 2-digit numbers
     */
    public static String orderNumbers(String number) {
        if(number != null) {
            if (number.length() % 2 == 0) {
                int numNumbers = number.length() / 2;
                int[] numbers = new int[numNumbers];
                for (int i = 0; i < numNumbers; i++) {
                    numbers[i] = Integer.parseInt(number.substring(i * 2, i * 2 + 2));
                }
                Arrays.sort(numbers);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < numNumbers; i++) {
                    sb.append(String.format(AppUtils.Companion.getCurrentLocale(), "%02d", numbers[i]));
                }
                return sb.toString();
            }
        }
        return "";
    }

    public void releaseRecognizer(){
        mTextRecognizer.release();
    }

    public static boolean createDirIfNotExists(String path) {
        boolean ret = true;

        File file = new File(Environment.getExternalStorageDirectory(), path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Log.e("TravellerLog :: ", "Problem creating Image folder");
                ret = false;
            }
        }
        return ret;
    }
}
