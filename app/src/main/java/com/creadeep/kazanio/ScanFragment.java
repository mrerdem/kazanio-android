package com.creadeep.kazanio;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import androidx.preference.PreferenceManager;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;

import com.google.android.gms.vision.text.TextRecognizer;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ScanFragment extends Fragment implements ImageReader.OnImageAvailableListener, HorizontalPagerAdapter.RowNumberListener {
  private Bitmap mRawFrameBitmap = null; // Original source bitmap from camera
  protected int previewWidth = 640; // Width of viewfinder bitmap in px
  protected int previewHeight = 480; // Height of viewfinder bitmap in px
  private int[] rgbBytes = null;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int yRowStride;
  private Runnable imageConverter;
  private Runnable postInferenceCallback;
  private boolean computingDetection = false;
  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
  private static final int MINIMUM_PREVIEW_SIZE = 320;
  private final int MY_PERMISSIONS_REQUEST_CAMERA = 999; // id to identify "camera" permission request
  private CameraDevice mCameraDevice;
  private AutoFitTextureView mTextureView; // TextureView for camera viewfinder on top_menu_scan layout
  private CameraManager manager;
  private Size mPreviewSize;
  private Size rawImageSize = DESIRED_PREVIEW_SIZE; // Although 640x480 is requested, Samsung phones return 720x480. This is used to get this info and use it to fix the problem.
  private Surface surface;

  private HandlerThread mBackgroundThread; // Camera background thread
  private Handler mBackgroundHandler;
  private CaptureRequest.Builder mPreviewBuilder;
  private ImageReader previewReader;
  private CameraCaptureSession mPreviewSession;
  private Handler mPreviewHandler;

  private Boolean isAnyInfoFound = false; // States if any of the information is detected
  private boolean[] isRowNumberFound; // Stores the same information as isAllRowsFound but for each individual row
  private Boolean isDateFound = false; // False: keep looking for the date and extracting digits
  private Boolean isFractionFound = false; // False: keep looking for the fraction
  private boolean isAllRowsFound = false;
  private boolean mIsActive = false; // used to run background process only when background thread is alive

  private Semaphore mCameraOpenCloseLock = new Semaphore(1);
  private TorchStatusListener torchStatusCallback; // Used to inform activity from events happening in fragment via interface callback
  private GameTypeListener gameTypeCallback; // Used to inform activity from events happening in fragment via interface callback
  private RowNumberListener2 rowNumberCallback2; // Used to inform activity from events happening in fragment via interface callback
  private cameraOperationListener cameraOperationCallback; // Used to monitor ongoing camera operations to disable bottom navigation to prevent too quick fragment switches causing exception
  private FragmentUiStatusListener fragmentUiStatusCallback; // Used to inform activity when fragment's views are available to be referenced in the tutorial

  private ViewPager2 horizontalViewPager; // horizontal view pager for different game types
  private HorizontalPagerAdapter horizontalPagerAdapter; // RecyclerView adapter that fills vertical pages into each horizontal page
  private int activeGameType; // 1: Milli Piyango, etc.
  private int activeRowNum; // starts from 1
  private int activeColumnNum; // Number of 2-digit numbers (or 1-digit numbers for MP)
  private TicketUtils ticketUtils;

  private Ticket bundleTicket; // Ticket to be passed to result fragment
  private Bundle resultBundle; // Contains data to be passed to the next fragment

  private SharedPreferences prefs;

  // Redundant data extraction variables
  private int minReqRedundancy = 3; // The minimum number of redundancy requirement for each information to be accepted (minReqRedundancy many is required out of minReqRedundancy + 1)
  private List<String> fractionSamples; // Stores minReqRedundancy + 1 many samples for fraction decision
  private List<String> numberSamplesMP; // Stores minReqRedundancy + 1 many samples for number decision for Milli Piyango (entire number is assumed as a sample instead of using individual digits since number of digits may be 6 or 7)
  private List<List<String>> dateSamples; // Stores 3 * (minReqRedundancy + 1) many samples for date decision
  private ArrayList<ArrayList<ArrayList<String>>> numberSamples; // Stores (numRows * numColumns) * (minReqRedundancy + 1) many samples for number decision
  private char[] verifiedDate; // Verified date decided using redundant date samples
  private char[] verifiedNumber; // Verified number decided using redundant date samples

  private String[] poi; // states which row/number is still to be detected. DataExtractor is informed using this to prevent extracting unnecessary data.
  private boolean isAllFound = false; // tells if fragment transaction has happened to prevent multiple consecutive transactions
  private boolean isScanningAnimationActive = false; // used to disable scanning animation when vertical page is changed

  private static Bundle savedInstanceStateScan; // Stores user UI state of ScanFragment to restore later

  private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
      //Log.e(TAG, "onSurfaceTextureAvailable, width=" + width + ",height=" + height);
      openCamera();
      // inform activity to make torch button visible
      torchStatusCallback.enableTorchButton();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
      //Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
      return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
      //Log.e(TAG, "onSurfaceTextureUpdated");
    }

  };

  private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

    @Override
    public void onOpened(CameraDevice camera) {
      mCameraDevice = camera;
      startPreview(); // Comment this line to replace camera preview with blank (&black) view
    }

    @Override
    public void onClosed(@NonNull CameraDevice camera) {
      super.onClosed(camera);
      stopBackgroundThread();
    }

    @Override
    public void onDisconnected(CameraDevice camera) {
    }

    @Override
    public void onError(CameraDevice camera, int error) {
    }
  };

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.fragment_scan, container, false);

    // Prepare texture view which will show camera real-time image
    mTextureView = v.findViewById(R.id.textureViewCamera);
    if (mTextureView.isAvailable()) {
      openCamera();
    } else {
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    // Prepare view pager which will hold pages made of overlay frames changed assigned to individual tabs and changed via swipe
    horizontalViewPager = v.findViewById(R.id.view_pager_horizontal);
    horizontalViewPager.setAdapter(horizontalPagerAdapter);
    horizontalPagerAdapter.setRowNumberListener(this);

    // Inform viewpager when a tab is manually selected
    // to show the names of different game types
    TabLayout tabLayout = v.findViewById(R.id.tabLayout);

    // Inform MainActivity when horizontal page is changed
    horizontalViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        super.onPageScrolled(position, positionOffset, positionOffsetPixels);
      }

      @Override
      public void onPageSelected(int position) {
        super.onPageSelected(position);
        // Reset animations on the current game & row before changing to another one
        if (activeGameType > 0 && activeRowNum > 0) {
          horizontalPagerAdapter.setScanningAnimationStatus(activeGameType, activeRowNum - 1, false);
          horizontalPagerAdapter.setInstructionAnimationStatus(activeGameType, activeRowNum - 1, true);
        }

        gameTypeCallback.onGameTypeChanged(position);

        // Update active row num
        int[] row_variations = getResources().getIntArray(R.array.game_row_variations);
        if (activeRowNum >= row_variations[position]) {
          activeRowNum = row_variations[position];
        }
        // Update active game type
        activeGameType = position + 1;
        horizontalPagerAdapter.setRowNumber(activeRowNum, position + 1);
      }

      @Override
      public void onPageScrollStateChanged(int state) {
        super.onPageScrollStateChanged(state);
      }
    });

    // Inform tab layout when viewpager is used to change current tab via swipe
    TabLayoutMediator tabLayoutMediator = new TabLayoutMediator(tabLayout, horizontalViewPager, true, new TabLayoutMediator.TabConfigurationStrategy() {
      @Override
      public void onConfigureTab(TabLayout.Tab tab, int position) {
        // position of the current tab and that tab
        String[] names = getResources().getStringArray(R.array.game_names_scan);
        tab.setText(names[position]);
      }
    });
    tabLayoutMediator.attach();

    return v;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Initialize image transformation tools
    mRawFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);

    // Set top menu
    setHasOptionsMenu(true);

    // Set pager adapter that will fill in horizontal overlay frames & tabs
    prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    horizontalPagerAdapter = new HorizontalPagerAdapter(getContext(), getResources().getStringArray(R.array.game_names_scan).length);

    // Set ticket utilities for processing image
    ticketUtils = new TicketUtils(getContext());
  }

  @Override
  public void onResume() {
    super.onResume();
    cameraOperationCallback.onCameraOperationStatusChanged(true); // Camera operations start here, end at onConfigured
    mIsActive = true;
    startBackgroundThread();
    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).
    if (mTextureView.isAvailable()) {
      openCamera();
    } else {
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }
    computingDetection = false;
    ticketUtils = new TicketUtils(getContext());

    // Set bundle to pass to result fragment
    resultBundle = new Bundle();

    // Read savedInstanceState to restore previous gameType and rowNum and viewPager status
    if (savedInstanceStateScan != null) {
      activeGameType = savedInstanceStateScan.getInt("activeGameType");
      activeRowNum = savedInstanceStateScan.getInt("activeRowNum");
    }
    // Nothing to restore, use default user preferences
    else {
      activeGameType = Integer.parseInt(prefs.getString("defaultGameType", "2"));
      if(activeGameType == 1) { // if game type is Milli Piyango
        activeRowNum = Integer.parseInt(prefs.getString("defaultMPTicketType", "1"));
      }
      else {
        activeRowNum = Integer.parseInt(prefs.getString("defaultRowNumber", "1"));
      }
    }
    changeGameType(activeGameType);
    gameTypeCallback.onGameTypeChanged(activeGameType - 1);
    horizontalPagerAdapter.setRowNumber(activeRowNum, activeGameType);
    // Set row number (number of 2-digit numbers in each row)
    if(activeGameType == 4) // On numara
      activeColumnNum = 10;
    else
      activeColumnNum = 6;

    // Reset point of interest
//    resetTicketInfo();

    // Initialize text recognition tool
    MainActivity.mTextRecognizer = new TextRecognizer.Builder(getContext()).build();

    // Inform activity saying fragment is ready so that tab layout can be referenced in the tutorial
    if (!prefs.getBoolean("isScanningTutorialShown", false))
      fragmentUiStatusCallback.onFragmentUiAvailable();
  }

  /**
   * Resets all the ticket related information that vary for each game/row type.
   * Executed when ticket type/row number is changed.
   */
  private void resetTicketInfo() {
    isRowNumberFound = new boolean[activeRowNum];
    isDateFound = false;
    isFractionFound = false;
    bundleTicket = new Ticket();
    isScanningAnimationActive = false;
    isAllFound = false;
    isAnyInfoFound = false;
    // Reset poi
    poi = new String[activeRowNum + 2]; // 2: fraction area & date area
    poi[0] = "1"; // Add poi for fraction row
    String tmp = new String(new char[activeColumnNum]).replace("\0", "1");
    for (int i=1; i<activeRowNum+1; i++) { // Add poi for all number rows
      poi[i] = tmp;
    }
    poi[activeRowNum+1] = "1"; // Add poi for date row
    // Reset redundant sampling variables
    fractionSamples = new ArrayList<>(); // Reset samples for fraction decision
    numberSamplesMP = new ArrayList<>(); // Reset samples for MP number decision
    dateSamples = new ArrayList<>(); // Reset samples for date decision
    numberSamples = new ArrayList<>(); // Reset samples for number decision
    verifiedDate = new char[8]; // Reset date information extracted from redundant samples
    if (activeGameType == 4)
      verifiedNumber = new char[activeRowNum * 20]; // Reset number information extracted from redundant samples
    else
      verifiedNumber = new char[activeRowNum * 12];
     for (int i = 0; i < activeRowNum; i++) {
      numberSamples.add(null);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    // Store current scanFragment status
    if (savedInstanceStateScan == null)
      savedInstanceStateScan = new Bundle();
    savedInstanceStateScan.putInt("activeGameType", activeGameType);
    savedInstanceStateScan.putInt("activeRowNum", activeRowNum);
    // Cancel camera related tasks
    cameraOperationCallback.onCameraOperationStatusChanged(true); // Camera closing operations start here, ends at closeCamera
    mIsActive = false;
    closeCamera();
    if (surface != null)
      surface.release();
    if (previewReader != null)
      previewReader.close();
    ticketUtils.releaseRecognizer();
  }

  private void openCamera() {
    // Camera permission is not granted
    // Start a background thread thread to be used by the camera looper
    //open the camera and proceed using mStateCallback
    //                mCameraOpenCloseLock.release();
    //    Log.i(TAG,"openCamera2");
    Thread openCameraThread = new Thread(new Runnable() {
      @Override
      public void run() {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { // Camera permission is not granted
          ActivityCompat.requestPermissions(getActivity(),
                  new String[]{Manifest.permission.CAMERA},
                  MY_PERMISSIONS_REQUEST_CAMERA);
        } else {

          manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);

          try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mPreviewSize =
                    chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                            DESIRED_PREVIEW_SIZE.getWidth(),
                            DESIRED_PREVIEW_SIZE.getHeight());

            // Start a background thread thread to be used by the camera looper
            if (mBackgroundHandler == null)
              startBackgroundThread();
            try {
              mCameraOpenCloseLock.acquire();
            } catch (InterruptedException e) {
              e.printStackTrace();
            } finally {
              manager.openCamera(cameraId, mStateCallback, mBackgroundHandler); //open the camera and proceed using mStateCallback
//                mCameraOpenCloseLock.release();
            }
          } catch (CameraAccessException e) {
            e.printStackTrace();
          }
        }
      }
    });
    openCameraThread.start();
  }

  /**
   * Callback for Camera2 API
   */
  @Override
  public void onImageAvailable(final ImageReader reader) {

    if(!isAllFound) {
      if (rgbBytes == null) {
        rgbBytes = new int[previewWidth * previewHeight];
      }
      final Image image;
      try {
        image = reader.acquireLatestImage();
        if (image == null) {
          return;
        }
        if (isProcessingFrame) {
          image.close();
          return;
        }
        isProcessingFrame = true;

        rawImageSize = new Size(image.getWidth(), image.getHeight()); // Get image size to fix problem on Samsung phones (image is 720x480 instead of 640x480)
        Trace.beginSection("imageAvailable");
        final Image.Plane[] planes = image.getPlanes();
        fillBytes(planes, yuvBytes);
        yRowStride = planes[0].getRowStride();
        final int uvRowStride = planes[1].getRowStride();
        final int uvPixelStride = planes[1].getPixelStride();

        imageConverter =
                () -> ImageUtils.convertYUV420ToARGB8888(
                        yuvBytes[0],
                        yuvBytes[1],
                        yuvBytes[2],
                        previewWidth,
                        previewHeight,
                        yRowStride,
                        uvRowStride,
                        uvPixelStride,
                        rgbBytes);

        postInferenceCallback =
                () -> {
                  image.close();
                  isProcessingFrame = false;
                };
        processImage();
      } catch (final Exception e) {
        Trace.endSection();
        return;
      }
      Trace.endSection();
    }
  }

  /*
  Inflate top menu
   */
  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
  }

  protected void processImage() { // Executed for each frame

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;

    mRawFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    if(mIsActive && !isAllFound)
      runInBackground(
              () -> {

                // Use extractor to read the ticket
                Context context = getActivity();
                Ticket resultTicket = ticketUtils.extractData(context, mRawFrameBitmap, activeGameType, poi);

                // Process fraction for Milli Piyango
                if (activeGameType == 1) {
                  // Process information for each active point of interest

                  // Fraction POI
                  if (poi[0].contains("1") && resultTicket.getGameType() == 1 && resultTicket.getFraction() != null) { // if fraction is still a point of interest, gameType check is required to prevent having "1" as fraction if swiped from sayisal loto before scan
                    String fraction = resultTicket.getFraction();
                    // Update samples list
                    int size = fractionSamples.size();
                    if (size == minReqRedundancy + 1) { // If it already contains required amount of samples
                      fractionSamples.remove(0); // Remove the oldest
                      fractionSamples.add(fraction); // Add a new one
                    } else { // If it has not reached its required amount yet
                      fractionSamples.add(fraction); // Add a new one
                      size++; // Increase the size information
                    }

                    // Decide if the most frequent is frequent enough
                    if (size >= minReqRedundancy) { // Evaluate only if there are sufficient samples
                      if (isSamplesSufficient(fractionSamples)) { // If the latest fraction satisfies the required redundancy
                        bundleTicket.setFraction(fraction); // accept the latest sample
                        poi[0] = "0"; // set poi as not of interest anymore
                        isFractionFound = true;
                        isAnyInfoFound = true;
                      }
                    }
                  }
                  bundleTicket.setTicketType(activeRowNum);
                } else {
                  if (resultTicket.getFraction() != null) {
                    bundleTicket.setFraction(resultTicket.getFraction());
                    poi[0] = "0";
                    isFractionFound = true;
                  }
                }

                // Process detected numbers
                int currentActiveRowNum = activeRowNum; // This is to prevent exception if activeRowNum is increased by user vertical swipe while the for loop iterates
                // Check if there is any undetected row
                isAllRowsFound = true;
                if (activeGameType == 1 && poi[1].contains("1"))
                  isAllRowsFound = false;
                else {
                  for (int i = 0; i < currentActiveRowNum; i++) {
                    if (poi[i + 1] != null && poi[i + 1].contains("1")) {
                      isAllRowsFound = false;
                      break;
                    }
                  }
                }
                if (!isAllRowsFound) {
                  String receivedNumber = resultTicket.getNumber();
                  if (receivedNumber != null && !TextUtils.extractDigits(receivedNumber).equals("")) { // If any information is extracted and if it contains any new readings (not XXX's only)
                    if ((activeGameType == 1 && (receivedNumber.length() == 6 || receivedNumber.length() == 7)) || // Milli Piyango
                            ((activeGameType == 2 || activeGameType == 3 || activeGameType == 5) && (receivedNumber.length() == 12 * currentActiveRowNum)) || // Sayisal Loto
                            (activeGameType == 4 && (receivedNumber.length() == 20 * currentActiveRowNum))) { // On Numara
                      // Single row number detection for Milli Piyango
                      if (activeGameType == 1) {
                        // Update samples list
                        int size = numberSamplesMP.size();
                        if (size == minReqRedundancy + 1) { // If it already contains required amount of samples
                          numberSamplesMP.remove(0); // Remove the oldest
                          numberSamplesMP.add(receivedNumber); // Add new number
                        } else {
                          numberSamplesMP.add(receivedNumber); // Add new number
                          size++; // Increase the size information
                        }

                        // Decide if the most frequent is frequent enough
                        if (size >= minReqRedundancy) { // Evaluate only if there are sufficient samples
                          if (isSamplesSufficient(numberSamplesMP)) { // If the latest number satisfies the required redundancy
                            bundleTicket.setNumber(receivedNumber); // accept the latest sample
                            poi[1] = "0"; // Set poi as not of interest anymore
                            isAllRowsFound = true;
                            isAnyInfoFound = true;
                          }
                        }
                      }
                      // Multiple row number detection for non Milli Piyango
                      else {
                        for (int i = 0; i < currentActiveRowNum; i++) { // Process each row
                          if (poi[i + 1] != null && poi[i + 1].contains("1")) { // if it contains point of interest
                            String number;
                            int numNumber = 6;
                            if (activeGameType != 4) // Loto
                              number = TextUtils.extractDigits(receivedNumber.substring(12 * i, 12 * (i + 1)));
                            else { // On Numara
                              number = TextUtils.extractDigits(receivedNumber.substring(20 * i, 20 * (i + 1)));
                              numNumber = 10;
                            }
                            if (activeGameType == 5 && number.length() == 12)
                              number = TicketUtils.orderNumbers(number.substring(0, 10)) + number.substring(10, 12);
                            else
                              number = TicketUtils.orderNumbers(number);
                            if (number.length() == numNumber * 2) { // On Numara
                              // Update samples list
                              int numSamples; // The number of the number samples stored in numberSamples List
                              if (numberSamples.get(i) != null && numberSamples.get(i).size() <= numNumber) { // Second check is required
                                if (numberSamples.get(i).get(0).size() == minReqRedundancy + 1) {
                                  for (int j = 0; j < numNumber; j++) {
                                    numberSamples.get(i).get(j).remove(0);
                                  }
                                }
                                for (int j = 0; j < numNumber; j++) {
                                  numberSamples.get(i).get(j).add(number.substring(j * 2, j * 2 + 2));
                                }
                                numSamples = numberSamples.get(i).get(0).size();
                              }
                              else {
                                ArrayList<ArrayList<String>> tmpList = new ArrayList<>();
                                for (int j = 0; j < numNumber; j++) {
                                  ArrayList<String> tmpList2 = new ArrayList<>();
                                  tmpList2.add(number.substring(j * 2, j * 2 + 2));
                                  tmpList.add(tmpList2);
                                }
                                numberSamples.set(i, tmpList);
                                numSamples = 1;
                              }

                              // Decide if the most frequent is frequent enough
                              if (numSamples >= minReqRedundancy) { // Evaluate only if there are sufficient samples
                                for (int j = 0; j < numNumber; j++) { // Analyze each info (day, month, year)
                                  if (verifiedNumber.length != numNumber * 2 * activeRowNum) // Update verifiedNumber length if the user swiped horizontally before scanning
                                    verifiedNumber = new char[activeRowNum * numNumber * 2];
                                  else {
                                    if (isSamplesSufficient(numberSamples.get(i).get(j))) { // If the latest date satisfies the required redundancy
                                      verifiedNumber[i * numNumber * 2 + j * 2] = number.charAt(j * 2);
                                      verifiedNumber[i * numNumber * 2 + j * 2 + 1] = number.charAt(j * 2 + 1);
                                    }
                                  }
                                }
                                // Check if all digits are decided
                                boolean all = true;
                                for (int j = 0; j < numNumber; j++) {
                                  if (verifiedNumber.length == numNumber * 2 * activeRowNum && verifiedNumber[i * numNumber * 2 + j * 2] == '\0') { // If empty (default value is intact)
                                    all = false;
                                    break;
                                  }
                                }
                                // Set if all digits are decided
                                if (all) {
                                  isRowNumberFound[i] = true;
                                  poi[i + 1] = "0"; // set as not of interest anymore
                                  isAnyInfoFound = true;
                                }
                              }
                            } else { // number for this row is not extracted, so make it null
                              if (numberSamples.size() == 0) {
                                numberSamples.add(null);
                              }
                            }
                          }
                        }

                        isAllRowsFound = true;
                        for (int i = 0; i < activeRowNum; i++) { // Process each row
                          if (!isRowNumberFound[i]) {
                            isAllRowsFound = false;
                          } else {
                            isAnyInfoFound = true;
                          }
                          if (!isAllRowsFound && isAnyInfoFound)
                            break;
                        }
                        if (isAllRowsFound)
                          bundleTicket.setNumber(String.valueOf(verifiedNumber)); // accept the latest sample
                      }
                    }
                  }
                }

                // Process date
                if (resultTicket.getDate() != null && ((activeGameType == 1 && (poi[2] != null && poi[2].contains("1"))) || (activeGameType != 1 && (poi[currentActiveRowNum + 1] != null && poi[currentActiveRowNum + 1].contains("1"))))) { // if it contains point of interest
                  String date = resultTicket.getDate();
                  // Update samples list
                  int size;
                  if (dateSamples.size() == 0)
                    size = 0;
                  else
                    size = dateSamples.get(0).size();
                  for (int i = 0; i < 3; i++) { // Process each information (day, month, year)
                    if (size == minReqRedundancy + 1) // If it already contains required amount of samples
                      dateSamples.get(i).remove(0); // Remove the oldest
                    if (size == 0) { // Add new samples for each information type directly if there is no previous samples
                      ArrayList<String> tmp = new ArrayList<>();
                      switch (i) {
                        case 0:
                          tmp.add(date.substring(0, 2));
                          break;
                        case 1:
                          tmp.add(date.substring(2, 4));
                          break;
                        case 2:
                          tmp.add(date.substring(4, 8));
                          break;
                      }
                      dateSamples.add(tmp);
                    } else { // Append the new sample for each information type into the previous ones
                      switch (i) {
                        case 0:
                          dateSamples.get(i).add(date.substring(0, 2));
                          break;
                        case 1:
                          dateSamples.get(i).add(date.substring(2, 4));
                          break;
                        case 2:
                          dateSamples.get(i).add(date.substring(4, 8));
                          break;
                      }
                    }
                  }
                  size++; // Increase the size information

                  // Decide if the most frequent is frequent enough
                  if (size >= minReqRedundancy) { // Evaluate only if there are sufficient samples
                    for (int i = 0; i < 3; i++) { // Analyze each info (day, month, year)
                      if (isSamplesSufficient(dateSamples.get(i))) { // If the latest date satisfies the required redundancy
                        switch (i) { // Update each information
                          case 0:
                            verifiedDate[0] = date.charAt(0);
                            verifiedDate[1] = date.charAt(1);
                            break;
                          case 1:
                            verifiedDate[2] = date.charAt(2);
                            verifiedDate[3] = date.charAt(3);
                            break;
                          case 2:
                            verifiedDate[4] = date.charAt(4);
                            verifiedDate[5] = date.charAt(5);
                            verifiedDate[6] = date.charAt(6);
                            verifiedDate[7] = date.charAt(7);
                            break;
                        }
                      }
                    }
                    // Check if all digits are decided
                    boolean all = true;
                    for (int i = 0; i < 8; i++) {
                      if (verifiedDate[i] == '\0') { // If empty (default value is intact)
                        all = false;
                        break;
                      }
                    }
                    // Set if all digits are decided
                    if (all) {
                      bundleTicket.setDate(date); // accept the latest sample
                      if (activeGameType == 1)
                        poi[2] = "0";
                      else
                        poi[currentActiveRowNum + 1] = "0"; // set as not of interest anymore
                      isDateFound = true;
                      isAnyInfoFound = true;
                    }
                  }
                }

                // Go to result fragment
                if (isAllRowsFound && isDateFound && isFractionFound) {
                  // Check date and number length match for milli piyango
                  if (activeGameType == 1 && !checkMPNumberLength(bundleTicket.getNumber(), bundleTicket.getDate())) {
                    isAllRowsFound = false;
                    poi[1] = "1";
                    isDateFound = false;
                    poi[2] = "1";
                  } else {
                    // Process data
                    bundleTicket.setGameType(resultTicket.getGameType());

                    // Upload last frame to server
                    uploadImage(context, mRawFrameBitmap, activeGameType, activeRowNum, bundleTicket);

                    // Store current fragment's state
                    if (savedInstanceStateScan == null)
                      savedInstanceStateScan = new Bundle();
                    savedInstanceStateScan.putInt("activeGameType", activeGameType);
                    savedInstanceStateScan.putInt("activeRowNum", activeRowNum);

                    // Go to result fragment
                    resultBundle.putSerializable("Ticket", bundleTicket);
                    ResultFragment resultFragment = new ResultFragment();
                    resultFragment.setArguments(resultBundle);
                    FragmentTransaction fragmentTransaction; // To change to a different fragment
                    fragmentTransaction = getActivity().getSupportFragmentManager().beginTransaction();
                    fragmentTransaction.setCustomAnimations(R.animator.flip_right_in, R.animator.flip_right_out, R.animator.flip_left_in, R.animator.flip_left_out);
                    fragmentTransaction.replace(R.id.fragment_container, resultFragment);
                    fragmentTransaction.addToBackStack(null); // To enable back button come back to scan fragment
                    fragmentTransaction.commit();
                    isAllFound = true; // To prevent consecutive fragmentTransactions into result fragment
                  }
                } else {
                  // Enable scanning animation after any row is read
                  if (isAnyInfoFound && !isScanningAnimationActive) {
                    horizontalPagerAdapter.setScanningAnimationStatus(activeGameType, activeRowNum - 1, true);
                    isScanningAnimationActive = true;
                    horizontalPagerAdapter.setInstructionAnimationStatus(activeGameType, activeRowNum - 1, false);
                  }
                  // Disable scanning animation after vertical page change
                  if (!isAnyInfoFound && isScanningAnimationActive) {
                    horizontalPagerAdapter.setScanningAnimationStatus(activeGameType, activeRowNum - 1, false);
                    isScanningAnimationActive = false;
                  }
                }
                computingDetection = false;
              });
    else
      computingDetection = false;
  }

  /**
   * Checks if the amount of digits in the number is correct by evaluating date
   * @param resultNumber number to be checked
   * @param resultDate date to be considered
   * @return true if no problem, false if problematic
   */
  private boolean checkMPNumberLength(String resultNumber, String resultDate) {
    return((resultNumber.length() == 7 && resultDate.substring(0,4).equals("3112")) || (resultNumber.length() == 6 && !resultDate.substring(0,4).equals("3112")));
  }

  /**
   * Checks if the provided samples possesses minReqRedundancy many redundant samples.
   * @param samples LinkedList that stores all the samples
   * @return Boolean that stores the result
   */
  private boolean isSamplesSufficient(List<String> samples) {
    for (int i = 0; i < samples.size(); i++) { // Loop all items
      int count = 0;
      for (int j = 0; j < samples.size(); j++) {
        if (i != j && samples.get(i).equals(samples.get(j))) { // Increase counter if the same
          count++;
          if (count == minReqRedundancy - 1) // Break out if count is sufficient
            break;
        }
      }
      if (count == minReqRedundancy - 1) {
        return true;
      }
    }
    return false;
  }

  protected static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
    final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
    final Size desiredSize = new Size(width, height);

    // Collect the supported resolutions that are at least as big as the preview Surface
    boolean exactSizeFound = false;
    final List<Size> bigEnough = new ArrayList<>();
    final List<Size> tooSmall = new ArrayList<>();
    for (final Size option : choices) {
      if (option.equals(desiredSize)) {
        // Set the size but don't return yet so that remaining sizes will still be logged.
        exactSizeFound = true;
      }

      if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
        bigEnough.add(option);
      } else {
        tooSmall.add(option);
      }
    }


    if (exactSizeFound) {
      return desiredSize;
    }

    // Pick the smallest of those, assuming we found any
    if (bigEnough.size() > 0) {
      final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
      return chosenSize;
    } else {
      return choices[0];
    }
  }

  private void startPreview() {

    if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
      return;
    }

    SurfaceTexture texture = mTextureView.getSurfaceTexture(); // SurfaceTexture "Captures frames from an image stream as an OpenGL ES texture."

    if (null == texture) {
      return;
    }

    texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
    surface = new Surface(texture);

    try {
      mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); // The type of mPreviewBuilder is CaptureRequest.Builder. We want to use preview mode not photo or video mode.

    } catch (CameraAccessException e) {
      e.printStackTrace();
      mCameraOpenCloseLock.release();
    }

    mPreviewBuilder.addTarget(surface);

    // Create the reader for the preview frames. (640x640 was taken from tensorflow_demo via Log) This makes onImageAvailable method to be triggered.
    previewReader =
            ImageReader.newInstance(
                    rawImageSize.getWidth(), rawImageSize.getHeight(), ImageFormat.YUV_420_888, 2);

    previewReader.setOnImageAvailableListener(this, mBackgroundHandler);

    mPreviewBuilder.addTarget(previewReader.getSurface());

    try {
      mCameraDevice.createCaptureSession(Arrays.asList(surface, previewReader.getSurface()), new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
          mPreviewSession = session;
          updatePreview();
          cameraOperationCallback.onCameraOperationStatusChanged(false); // Camera opening operations finish here, starts at onResume
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
        }
      }, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
      mCameraOpenCloseLock.release();
    }
  }

  private void updatePreview() {

    mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO); //CONTROL_MODE: auto focus, exposure and wb. CONTROL_MODE_AUTO: auto focus, exposure and wb. This has to be sent for each frame we receive back from CameraDevice
    HandlerThread mPreviewThread = new HandlerThread("CameraPreview");
    mPreviewThread.start();
    mPreviewHandler = new Handler(mPreviewThread.getLooper());

    try {
      if(mPreviewSession != null)
        mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mPreviewHandler); // CaptureRequest.Builder.build() returns CaptureRequest
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
    mCameraOpenCloseLock.release();
  }

  private void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  private void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  private int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  private synchronized void runInBackground(final Runnable r) {
    if (mIsActive && mBackgroundHandler != null) {
      mBackgroundHandler.post(r);
    }
  }

  // Receiving torch status from activity
  public void setTorchStatusListener(TorchStatusListener callback) {
    this.torchStatusCallback = callback;
  }

  /*
  Relays the selected row number to child fragment
   */
  public void setRowNum(Integer valueOf) {
    horizontalPagerAdapter.setRowNumber(valueOf, horizontalViewPager.getCurrentItem() + 1);
  }

  public void setGameType(int type) {
    activeGameType = type;
    if (activeRowNum == 0) {
      if (activeGameType == 1) { // if game type is Milli Piyango
        activeRowNum = Integer.parseInt(prefs.getString("defaultMPTicketType", "1"));
      }
      else {
        activeRowNum = Integer.parseInt(prefs.getString("defaultRowNumber", "1"));
      }
    }
    resetTicketInfo();
  }

  public void changeGameType(int type) { // Used for changing to default game type on fragment load
    horizontalViewPager.setCurrentItem(type - 1, false);
    setGameType(type);
  }

  // This interface can be implemented by the Activity or parent Fragment
  public interface TorchStatusListener {
    void enableTorchButton(); // To enable button after textureview is available
  }
  // Receiving game type from horizontal viewPager
  public void setGameTypeListener(GameTypeListener callback) {
    this.gameTypeCallback = callback;
  }
  // This interface can be implemented by the Activity or parent Fragment
  public interface GameTypeListener {
    void onGameTypeChanged(int index);
  }
  // Receiving row number from horizontal viewPager (which receives it from vertical viewPager)
  public void setRowNumberListener2(RowNumberListener2 callback) {
    this.rowNumberCallback2 = callback;
  }
  // This interface can be implemented by the Activity or parent Fragment
  public interface RowNumberListener2 {
    void onRowNumberChanged2(int rowNum);
  }

  /**
   * Used to inform MainActivity about ScanFragment's ongoing camera operations.
   * This is needed to deactivate bottom navigation buttons to prevent too fast fragment switches
   * that cause camera opening and closing tasks overlap.
   */
  public interface cameraOperationListener {
    void onCameraOperationStatusChanged(boolean state); // Used to deactivate botttom navigation buttons
  }
  public void setCameraOperationListener(cameraOperationListener callback) {
    this.cameraOperationCallback = callback;
  }

  public interface FragmentUiStatusListener {
    void onFragmentUiAvailable(); // Used to start tutorial that uses fragment's tab layout view
  }
  public void setFragmentUiAvailableListener(FragmentUiStatusListener callback) {
    this.fragmentUiStatusCallback = callback;
  }

  /*
  Receives row (vertical page) index change from Horizontal Pager Adapter
  There is another one this class implements with the same name
  It is used to relay the same data to Main Activity
  */
  public void onRowNumberChanged(int rowNum, int gameType) {
    if (gameType == activeGameType) { // Prevent updating item action according to changes on invisible vertical/horizontal pages
      activeRowNum = rowNum;
      rowNumberCallback2.onRowNumberChanged2(rowNum);
      resetTicketInfo();
    }
  }

  /*
  Used by MainActivity to enable/disable torch
  */
  public void setTorch(boolean status){
    if(status) { // enable
      mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
      try {
        if(mPreviewSession != null)
          mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mPreviewHandler);
      } catch (CameraAccessException e) {
        e.printStackTrace();
      }
    }
    else{ // disable
      mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
      try {
        if(mPreviewSession != null)
          mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mPreviewHandler);
      } catch (CameraAccessException e) {
        e.printStackTrace();
      }
    }
  }

  private void startBackgroundThread() {
    if (mBackgroundThread != null) // Required to prevent viewfinder freeze when back button is pressed while settings activity was being opened
      mBackgroundThread.quit();
    mBackgroundThread = new HandlerThread("CameraBackground");
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  private void stopBackgroundThread() {
    Thread thread = new Thread(() -> {
      mBackgroundThread.quitSafely();
      try {
        mBackgroundThread.join();
        mBackgroundThread = null;
        mBackgroundHandler = null;
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    thread.start();
  }

  private void closeCamera(){
    // Close camera operations ends here, started at onPause
    Thread closeCameraThread = new Thread(() -> {
      try {
        mCameraOpenCloseLock.acquire();
        if (null != mPreviewSession) {
          mPreviewSession.close();
          mPreviewSession = null;
        }
        if (null != mCameraDevice) {
          mCameraDevice.close();
          mCameraDevice = null;
        }
      } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
      } finally {
        mCameraOpenCloseLock.release();
        cameraOperationCallback.onCameraOperationStatusChanged(false); // Close camera operations ends here, started at onPause
      }
    });
    closeCameraThread.start();
  }

  /**
   * Compares two {@code Size}s based on their areas.
   */
  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(final Size lhs, final Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
              (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  // Converts bitmap into png and uploads to the server
  private void uploadImage(Context context, Bitmap bitmap, int gameType, int ticketType, Ticket ticket){
    // Rotate image 90 degrees cw
    Matrix matrix = new Matrix();
    matrix.postRotate(90);
    Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

    // Save image to temp location
    try {
      File cachePath = new File(context.getCacheDir(), "images");
      cachePath.mkdirs(); // don't forget to make the directory
      FileOutputStream stream = new FileOutputStream(cachePath + "/image.jpg"); // overwrites this image every time
      rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
      stream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Get image uri
    File imagePath = new File(context.getCacheDir(), "images");
    File newFile = new File(imagePath, "image.jpg");
    Uri contentUri = FileProvider.getUriForFile(context, "com.creadeep.kazanio.fileprovider", newFile);

  }

}
