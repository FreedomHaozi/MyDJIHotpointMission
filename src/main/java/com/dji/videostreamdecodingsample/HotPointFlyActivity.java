package com.dji.videostreamdecodingsample;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatSeekBar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

import dji.common.error.DJIError;
import dji.common.flightcontroller.DJIFlightControllerCurrentState;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.flightcontroller.DJIFlightController;
import dji.sdk.flightcontroller.DJIFlightControllerDelegate;
import dji.sdk.missionmanager.DJIHotPointMission;
import dji.sdk.missionmanager.DJIMission;
import dji.sdk.missionmanager.DJIMissionManager;
import dji.sdk.products.DJIAircraft;

import static com.dji.videostreamdecodingsample.VideoDecodingApplication.FLAG_CONNECTION_CHANGE;
import static com.dji.videostreamdecodingsample.VideoDecodingApplication.getProductInstance;

public class HotPointFlyActivity extends AppCompatActivity implements View.OnClickListener {
    //    相关参数heading
    List<DJIHotPointMission.DJIHotPointHeading> heading = new ArrayList<>();

    //activity 控件
    private TextView hpfly_altitudeTv;
    private AppCompatSeekBar hpfly_altitude;
    private Spinner hpmissionheading;
    private AppCompatSeekBar adjustAltitude;
    //通过调节角速度来控制飞行器的飞行速度
    private TextView hpfly_velocity;
    private AppCompatSeekBar adjustvelocity;

    //当前飞行器gps坐标
    private double currentlatitude = 0; //当前飞行器纬度
    private double currentlongitude = 0; //当前飞行器经度
    private double targetlatitude = 34;  //纬度
    private double targetlongitude = 108;//经度
    private double altitude = 10;//高度
    //旋转角速度
    private float angularVelocity = 2f;//角速度
    private double radius = 6.0; //旋转半径
    //是否顺时针旋转
    private boolean isClockwise = true;

    //飞行器飞行基本参数
    DJIHotPointMission.DJIHotPointHeading mHPointHeading = DJIHotPointMission.DJIHotPointHeading.UsingInitialHeading;
    DJIHotPointMission.DJIHotPointStartPoint msStartPoint = DJIHotPointMission.DJIHotPointStartPoint.Nearest;  //设置环绕的起始方向
    DJIHotPointMission.DJIHotpointMissionExecutionState mExecutionState;
    DJIHotPointMission.DJIHotPointMissionStatus mMissionStatus;
    DJIFlightController mFlightController;
    //任务
    private DJIMissionManager mMissionManager;
    private DJIHotPointMission mHotPointMission;

    //监听设备连接情况
    BroadcastReceiver mreReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectChangeStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                    Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                    Manifest.permission.READ_PHONE_STATE
            }, 1);
        }
        final DJIBaseProduct mproduct =getProductInstance();
        if ((mproduct != null) && (mproduct.isConnected())) {
            setResultToToast(mproduct.getModel().name() + " 连接成功");
        } else {
            setResultToToast("未找到设备");
        }

        IntentFilter intentFilter = new IntentFilter(FLAG_CONNECTION_CHANGE);
        registerReceiver(mreReceiver, intentFilter);
        setContentView(R.layout.activity_hot_point_fly);
        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initFlightController();
        initDJIMissionManager();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mreReceiver);
    }



    private void initUI() {
        hpfly_altitudeTv = (TextView) findViewById(R.id.hpfly_altitudeTv);
        hpfly_altitude = (AppCompatSeekBar) findViewById(R.id.hpfly_altitude);
        hpmissionheading = (Spinner) findViewById(R.id.hpmissionHeading);
        adjustAltitude = (AppCompatSeekBar) findViewById(R.id.hpfly_altitude);
        hpfly_velocity = (TextView) findViewById(R.id.hpfly_velocityTv);
        adjustvelocity = (AppCompatSeekBar) findViewById(R.id.hpfly_velocity);
        adjustAltitude.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int currAltitude = progress + 10;
                hpfly_altitudeTv.setText(currAltitude + "m");
                altitude = currAltitude;
                if (mHotPointMission != null) {
                    mHotPointMission.altitude = currAltitude;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        adjustvelocity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                angularVelocity = (float) ((double) progress / radius);
                hpfly_velocity.setText(progress + "m/s");
                if (mHotPointMission != null) {
                    mHotPointMission.angularVelocity = angularVelocity;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        heading.add(DJIHotPointMission.DJIHotPointHeading.AlongCircleLookingBackwards);
        heading.add(DJIHotPointMission.DJIHotPointHeading.AlongCircleLookingForwards);
        heading.add(DJIHotPointMission.DJIHotPointHeading.AwayFromHotPoint);
        heading.add(DJIHotPointMission.DJIHotPointHeading.ControlledByRemoteController);
        heading.add(DJIHotPointMission.DJIHotPointHeading.TowardsHotPoint);
        heading.add(DJIHotPointMission.DJIHotPointHeading.UsingInitialHeading);
        hpmissionheading.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mHPointHeading = heading.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }


    private void initDJIMissionManager() {
        final DJIBaseProduct mproduct =getProductInstance();
        if ((mproduct != null) && (mproduct.isConnected())) {
            mMissionManager = mproduct.getMissionManager();
            mMissionManager.setMissionProgressStatusCallback(new DJIMissionManager.MissionProgressStatusCallback() {
                @Override
                public void missionProgressStatus(DJIMission.DJIMissionProgressStatus djiMissionProgressStatus) {
                }
            });
            mMissionManager.setMissionExecutionFinishedCallback(new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        setResultToToast("任务执行成功");
                    } else {
                        setResultToToast(djiError.getDescription());
                    }

                }
            });
            mHotPointMission = new DJIHotPointMission();
            configHotpointMission();
        } else {
            mMissionManager = null;
        }
    }


    //配置飞行任务
    private void configHotpointMission() {
//        正在加载配置信息
        if (mHotPointMission != null) {
            if (checkGpsCoordination(currentlatitude, currentlongitude)) {
                mHotPointMission.altitude = altitude;
                mHotPointMission.angularVelocity = angularVelocity;
                mHotPointMission.heading = mHPointHeading;
                mHotPointMission.latitude = targetlatitude;
                mHotPointMission.longitude = targetlongitude;
                mHotPointMission.isClockwise = isClockwise;
                mHotPointMission.radius = radius;
                mHotPointMission.startPoint = msStartPoint;
                setResultToToast("load drone parameters success");
            }
        }
    }

    //准备飞行任务
    private void prepareHotPointMission() {
        if (mHotPointMission != null && mMissionManager != null) {
            initDJIMissionManager();
            DJIMission.DJIMissionProgressHandler mhandler = new DJIMission.DJIMissionProgressHandler() {
                @Override
                public void onProgress(DJIMission.DJIProgressType djiProgressType, float v) {

                }
            };
            mMissionManager.prepareMission(mHotPointMission, mhandler, new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                    } else {
                        setResultToToast(djiError.getDescription());
                    }
                }
            });
            setResultToToast("热点飞行任务就绪");
        }
    }

    //开启飞行任务
    private void startHotPointMission() {
        if (mHotPointMission != null && mMissionManager != null) {
            mMissionManager.startMissionExecution(new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        setResultToToast("飞行任务开始");
                    } else {
                        setResultToToast("开始飞行阶段出错,请重置飞行参数");
                        setResultToToast(djiError.getDescription());
                    }
                }
            });
        }
    }

    //停止飞行任务
    private void stopHotPointMission() {
        if (mHotPointMission != null && mMissionManager != null) {
            mMissionManager.stopMissionExecution(new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {

                    } else {
                        setResultToToast(djiError.getDescription());
                    }
                }
            });
            setResultToToast("热点飞行任务停止");
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.hpmission_parpare: {
                prepareHotPointMission();
                break;
            }
            case R.id.hpmission_start: {
                startHotPointMission();
                break;
            }
            case R.id.hpmission_stop: {
                stopHotPointMission();
                break;
            }
        }
    }

    private void onProductConnectChangeStatus() {
        initFlightController();
        initDJIMissionManager();
    }

    private void initFlightController() {
        //获取飞行控制权
        final DJIBaseProduct mproduct = getProductInstance();
        if (mproduct != null && mproduct.isConnected()) {
            if (mproduct instanceof DJIAircraft) {
                mFlightController = ((DJIAircraft) mproduct).getFlightController();
                //获取当前状态
                Toast.makeText(HotPointFlyActivity.this, "热点飞行任务，当前飞机的经度为：" + mFlightController.getCurrentState().getAircraftLocation().getLongitude() + ",当前纬度为：" + mFlightController.getCurrentState().getAircraftLocation().getLatitude(), Toast.LENGTH_SHORT).show();
                mFlightController.setUpdateSystemStateCallback(new DJIFlightControllerDelegate.FlightControllerUpdateSystemStateCallback() {
                    @Override
                    public void onResult(DJIFlightControllerCurrentState djiFlightControllerCurrentState) {
                        currentlatitude = djiFlightControllerCurrentState.getAircraftLocation().getAltitude();
                        currentlongitude = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    }
                });
            }
        }
    }

    private void setResultToToast(final String string) {
        HotPointFlyActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(HotPointFlyActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    //    检查GPS是否满足应用要求
    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }
}
