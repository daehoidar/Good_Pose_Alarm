package com.example.goodposealarm;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.channels.FileChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.tensorflow.lite.Interpreter;


public class MainActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    // 변수 선언
    ConstraintLayout MainLayout; // 메인 레이아웃
    TextView goodOrBadText; // 자세가 좋은지 나쁜지 알려줄 텍스트

    SurfaceView surfaceView; // 카메라로 찍힌 화면을 출력할 뷰
    SurfaceHolder surfaceHolder; // 카메라 정보를 서피스뷰에 보내는 역할
    SurfaceHolder tempSurfaceHolder; // 임시 저장
    ImageView imageView;
    Camera camera; // 카메라
    int countCamera;
    int cameraFront, cameraBack;
    boolean frontOrBack = false;
    boolean cameraOnOrOff = false;
    public Bitmap shareBitmap;
    public Bitmap resizedBitmap;
    Button frontOrBackButton;

    Button goToLeaderboardButton; // 리더보드 레이아웃으로 가는 버튼

    Switch classificationSwitch; // 언제 측정할지를 받아줄 스위치
    Chronometer countBestTimeChronometer; // 좋은 자세로 앉아있던 시간을 알려주는 타이머

    Interpreter interpreter;
    String[] labels = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};


    ConstraintLayout LeaderboardLayout; // 리더보드 레이아웃

    Button goToMainButton; // 메인 레이아웃으로 돌아가는 버튼

    TextView leaderboardText1; // 기록 1
    TextView leaderboardText2; // 기록 2
    TextView leaderboardText3; // 기록 3
    TextView leaderboardText4; // 기록 4
    TextView leaderboardText5; // 기록 5

    boolean goodOrBad; // 자세가 좋은지 나쁜지 0과 1로 받아 줄 불리언언
    long goodPoseKeepTime; // 지속시간 담을 변수
    long[] leaderboardArray = new long[5];

   @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 위에 구린 바 없애기
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
               WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // 권한 추가
        ArrayList<String> permissions = new ArrayList<String>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
           permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
           permissions.add(Manifest.permission.CAMERA);

        if (permissions.size() > 0) {
           String[] reqpermissions = new String[permissions.size()];
           reqpermissions = permissions.toArray(reqpermissions);
           ActivityCompat.requestPermissions(this, reqpermissions, 2);
        }

        // 변수 값 넣어주기
        MainLayout = (ConstraintLayout) findViewById(R.id.MainLayout);

        classificationSwitch = (Switch) findViewById(R.id.classificationSwitch);
        countBestTimeChronometer = (Chronometer) findViewById(R.id.countBestTimeChronometer);

        goodOrBadText = (TextView) findViewById(R.id.goodOrBadText);
        goodOrBad = true;

        goToLeaderboardButton = (Button) findViewById(R.id.goToLeaderboardButton);

        surfaceView = (SurfaceView) findViewById(R.id.cameraView);
        imageView = (ImageView) findViewById(R.id.imageView);
        frontOrBackButton = (Button) findViewById(R.id.frontOrBackButton);


        LeaderboardLayout = (ConstraintLayout) findViewById(R.id.LeaderboardLayout);

        leaderboardText1 = (TextView) findViewById(R.id.leaderboardText1);
        leaderboardText2 = (TextView) findViewById(R.id.leaderboardText2);
        leaderboardText3 = (TextView) findViewById(R.id.leaderboardText3);
        leaderboardText4 = (TextView) findViewById(R.id.leaderboardText4);
        leaderboardText5 = (TextView) findViewById(R.id.leaderboardText5);

        goToMainButton = (Button) findViewById(R.id.goToMainButton);

        // 초기 레이아웃 설정
        MainLayout.bringToFront();
        goodOrBadText.bringToFront();
        init();

        // 위젯과의 상호작용
        classificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                    // 자세가 좋은지 나쁜지 알려줄 텍스트 설정
                    setGoodOrBadText();

                    // 카메라 키기
                    cameraOnOrOff = true;
                    camera.startPreview();
                    importCameraCallback();


                    countBestTimeChronometer.setBase(SystemClock.elapsedRealtime());
                    countBestTimeChronometer.start();
                } else {
                    getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                    // 카메라 끄기
                    cameraOnOrOff = false;
                    camera.stopPreview();
                    camera.setPreviewCallback(null);

                    goodPoseKeepTime = (SystemClock.elapsedRealtime() - countBestTimeChronometer.getBase()) / 1000; // Good Pose로 있던 시간 계산
                    countBestTimeChronometer.stop();
                    countBestTimeChronometer.setBase(SystemClock.elapsedRealtime());

                    // 최고기록 TOP 5 만들기
                    leaderboardArrayInsertAndSort();

                    // 리더보드에 기록
                    setLeaderboardText();

                    // 제세가 좋은지 나쁜지 알려줄 텍스트 스위치 꺼짐으로 설정
                    goodOrBadText.setText("스위치 꺼짐");
                    goodOrBadText.setTextColor(Color.rgb(255,255,255));
                }
            }
        });

        // 리더보드 레이아웃 앞으로 갖고오기
        goToLeaderboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LeaderboardLayout.bringToFront();
            }
        });

        // 메인 레이아웃 앞으로 갖고오기
        goToMainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainLayout.bringToFront();
            }
        });

    }

    void leaderboardArrayInsertAndSort() {
        if (goodPoseKeepTime > leaderboardArray[4]) {
            if(leaderboardArray[3] != 0) {
                leaderboardArray[4] = goodPoseKeepTime;
            } else {
                leaderboardArray[3] = goodPoseKeepTime;
            }
            Arrays.sort(leaderboardArray);
            // 역순 정렬
            for(int i = 0; i < 5 / 2; i++){
                long t;
                t = leaderboardArray[i];
                leaderboardArray[i] = leaderboardArray[leaderboardArray.length - i - 1];
                leaderboardArray[leaderboardArray.length - i - 1] = t;
            }
        }
    }

    void setLeaderboardText() {
        if (leaderboardArray[0] != 0) {
            leaderboardText1.setText("1. " + leaderboardArray[0] / 3600 + "시간" + leaderboardArray[0] / 60 % 60 + "분" + leaderboardArray[0] % 60 + "초");
        }
        if (leaderboardArray[1] != 0) {
            leaderboardText2.setText("2. " + leaderboardArray[1] / 3600 + "시간" + leaderboardArray[1] / 60 % 60 + "분" + leaderboardArray[1] % 60 + "초");
        }
        if (leaderboardArray[2] != 0) {
            leaderboardText3.setText("3. " + leaderboardArray[2] / 3600 + "시간" + leaderboardArray[2] / 60 % 60 + "분" + leaderboardArray[2] % 60 + "초");
        }
        if (leaderboardArray[3] != 0) {
            leaderboardText4.setText("4. " + leaderboardArray[3] / 3600 + "시간" + leaderboardArray[3] / 60 % 60 + "분" + leaderboardArray[3] % 60 + "초");
        }
        if (leaderboardArray[4] != 0) {
            leaderboardText5.setText("5. " + leaderboardArray[4] / 3600 + "시간" + leaderboardArray[4] / 60 % 60 + "분" + leaderboardArray[4] % 60 + "초");
        }
    }

    void setGoodOrBadText() {
        if (goodOrBad) {
           goodOrBadText.setTextColor(Color.rgb(0, 255, 0));
           goodOrBadText.setText("Good");
        } else {
           goodOrBadText.setTextColor(Color.rgb(255, 0, 0));
           goodOrBadText.setText("Bad");
        }
    }

    public void setFrontOrBack(View v) {
        if (cameraOnOrOff) {
            if (frontOrBack == false) {
                camera.setPreviewCallback(null);

                camera.stopPreview();
                camera.release();
                camera = null;

                camera = Camera.open(cameraFront);
                camera.setDisplayOrientation(90);


                try {
                    camera.setPreviewDisplay(tempSurfaceHolder);
                } catch(IOException e) {}

                camera.setPreviewCallback(this);
                camera.startPreview();

                frontOrBack = !frontOrBack;
            } else {
                camera.setPreviewCallback(null);

                camera.stopPreview();
                camera.release();
                camera = null;

                camera = Camera.open(cameraBack);
                camera.setDisplayOrientation(90);

                try {
                    camera.setPreviewDisplay(tempSurfaceHolder);
                } catch(IOException e) {}

                camera.setPreviewCallback(this);
                camera.startPreview();

                frontOrBack = !frontOrBack;

            }
        }
    }

    void importCameraCallback() {
        camera.setPreviewCallback(this);
    }

    private void init() {
        // 서피스홀더 서피뷰 데이터를 주고받게 함
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        countCamera = Camera.getNumberOfCameras();

        for(int i = 0; i < countCamera; i++) {
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraFront = i;
            }
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraBack = i;
            }
        }

        try {
            if (camera == null) {
                camera = Camera.open(cameraBack); // 카메라 안켜져있으면 열기

                camera.setDisplayOrientation(90); // 각도 조정

                camera.setPreviewDisplay(surfaceHolder); // 홀더와 카메라 연결

                tempSurfaceHolder = surfaceHolder;
            }
        } catch (IOException e) {    }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Camera.Parameters params  = camera.getParameters();
        int w = params.getPreviewSize().width;
        int h = params.getPreviewSize().height;
        int format = params.getPreviewFormat();

        YuvImage image = new YuvImage(data, format, w, h, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rect area = new Rect(0, 0, w, h);
        image.compressToJpeg(area, 100, out);
        Bitmap bm = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());

        Matrix matrix = new Matrix();

        // 좌우반전
        if (frontOrBack == false) {
            matrix.setScale(-1, 1);
            matrix.postTranslate(w, 0);
        }

        // 90 도 돌리기
        matrix.postRotate(90);

        Bitmap rotatedBitmap = Bitmap.createBitmap(bm, 0, 0, w, h, matrix, true);
        shareBitmap = rotatedBitmap;

        resizedBitmap = Bitmap.createScaledBitmap(shareBitmap, 224, 224, true);

        imageView.setImageBitmap(shareBitmap);
    }

    private MappedByteBuffer loadModelFile(String path) throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(path);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer modelFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

        return modelFile;
    }
}