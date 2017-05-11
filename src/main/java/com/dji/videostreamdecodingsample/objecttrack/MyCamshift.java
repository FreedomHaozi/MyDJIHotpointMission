package com.dji.videostreamdecodingsample.objecttrack;

import android.os.Environment;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG;
import org.opencv.video.Video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.opencv.imgproc.Imgproc.COLOR_RGB2GRAY;
import static org.opencv.imgproc.Imgproc.INTER_AREA;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.boundingRect;
import static org.opencv.imgproc.Imgproc.calcBackProject;
import static org.opencv.imgproc.Imgproc.contourArea;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.dilate;
import static org.opencv.imgproc.Imgproc.findContours;
import static org.opencv.imgproc.Imgproc.medianBlur;
import static org.opencv.imgproc.Imgproc.threshold;

/**
 * Created by 彪悍的小Y on 2017/5/9.
 */

public class MyCamshift {
    Rect rt;
    Mat frame;          //当前帧
    Mat foreground;     //前景
    Mat bw;             //中间二值变量
    Mat se;             //形态学结构元素
    //获取截取帧的位置路径
    static String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/testvideo.mp4";
    static String PATHPARENT = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/";

    /**
     *
     * @param preFrame  前一帧图片
     * @param nextFrame 后一帧图片
     * @return
     */
    public boolean waitingobject(Mat preFrame,Mat nextFrame){
        Mat interFrame=new Mat(CvType.CV_8UC1);
        cvtColor(preFrame,preFrame,COLOR_RGB2GRAY);
        cvtColor(preFrame,preFrame,COLOR_RGB2GRAY);

        Core.absdiff(preFrame ,nextFrame,interFrame);
        Mat erodeMat=Imgproc.getStructuringElement(MORPH_RECT,new Size(5,5));
        Mat dilateMat=Imgproc.getStructuringElement(MORPH_RECT, new Size(5,5));
        //进行开运算
        medianBlur(interFrame, interFrame, 3);
        Imgproc.erode(interFrame,interFrame,erodeMat);
        Imgproc.erode(interFrame,interFrame,erodeMat);
        Imgproc.dilate(interFrame,interFrame,dilateMat);
        Imgproc.dilate(interFrame,interFrame,dilateMat);
        threshold(interFrame,interFrame,100,255,THRESH_BINARY);
        return true;
    }



    //进行背景建模
    public Mat foregroundModel() {
        Mat src;
        BackgroundSubtractorMOG mog = new BackgroundSubtractorMOG();
        Mat foreground = new Mat();
        for (int i = 1; i < 5; i++) {
            src = Highgui.imread(PATHPARENT + "Street_" + i + ".jpg", Highgui.CV_LOAD_IMAGE_ANYCOLOR);
            mog.apply(src, foreground, 0.01);
            src.release();
        }
        return foreground;
    }

    /**
     * @param currFramePath 当前要处理帧的存储路径
     * @param foreground    通过高斯背景建模得到的目标背景
     * @param result        最终的单帧追踪结果
     * @return 返回当前帧中存在的可疑目标个数
     * TODO 手机上速度是硬伤
     * @author haozi
     */
    public int camshiftopt(String currFramePath, Mat foreground, Mat result) {
        //// TODO: 2017/5/2 查看传入的foregound是否正常
        Highgui.imwrite(PATHPARENT+"foreground.jpg",foreground);

        Mat frame = Highgui.imread(currFramePath);
        frame.copyTo(result);
        Mat se ;             //形态学结构元素
        Rect rt;

        //获取膨胀学形态结构
        se = Imgproc.getStructuringElement(MORPH_RECT, new Size(5, 5));
        //统计直方图时所用到的变量
        //需要统计的图像列表
        List<Mat> vecImg = new ArrayList<>();
//    统计图像的通道数
        MatOfInt vecChannel = new MatOfInt(0);
        //统计的直方图列数
        MatOfInt vecHistSize = new MatOfInt(32);
//    直方图的统计范围
        MatOfFloat vecRange = new MatOfFloat(0,240);
//统计前景目标像素位置的掩膜
        Mat mask = Mat.zeros(frame.rows(), frame.cols(), CvType.CV_8UC1);

        Mat hsv = new Mat();                //HSV颜色空间，在色调H上跟踪目标（camshift是基于颜色直方图的算法）
        Mat hist = new Mat();              //直方图数组
        double maxVal = 0;          //直方图最大值，为了便于投影图显示，需要将直方图规一化到[0 255]区间上
        Mat backProject = new Mat();        //反射投影
        MatOfInt4 hierarchy = new MatOfInt4();

//        //检测目标
//        BackgroundSubtractorMOG mog = new BackgroundSubtractorMOG();
//        mog.apply(frame, foreground, 0.01);

        //对前景进行中值滤波、形态学膨胀操作
        medianBlur(foreground, foreground, 5);
        Highgui.imwrite(PATHPARENT+"中值滤波后.jpg",foreground);
        Imgproc.dilate(foreground, foreground, se);
        Highgui.imwrite(PATHPARENT+"膨胀后.jpg",foreground);
        //对于直线追踪中背景缓慢变化的情况
        Imgproc.threshold(foreground,foreground,150,255,THRESH_BINARY);
        List<MatOfPoint> contours = new ArrayList<>();

        // TODO: 2017/5/4 二值化后图像
        Highgui.imwrite(PATHPARENT+"二值化后.jpg",foreground);
        Imgproc.findContours(foreground, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);
        if (contours.size() < 1) {
            return 0;
        }
        //对连通分量进行排序
        Collections.sort(contours, new Comparator<MatOfPoint>() {
            @Override
            public int compare(MatOfPoint o1, MatOfPoint o2) {
                if (Imgproc.contourArea(o1) > Imgproc.contourArea(o2)) {
                    return 1;
                } else if (Imgproc.contourArea(o1) - Imgproc.contourArea(o2) < 0.000001 && Imgproc.contourArea(o1) - Imgproc.contourArea(o2) > -0.000001) {
                    return 0;
                } else {
                    return -1;
                }
            }
        });
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_RGB2HSV);
        // TODO: 2017/5/4  提取出来色彩通道
        Highgui.imwrite(PATHPARENT+"H通道.jpg",hsv);
        vecImg.clear();
        vecImg.add(hsv);

        for (int k = 0; k < contours.size(); ++k) {
            //第k个连通分量的外接矩形框
            //设置最大的目标与最小目标的最大面积比为5:1
            if (contourArea(contours.get(k)) < contourArea(contours.get(0)) / 5)
                break;

            //对H分量的前景目标画出边界框
            rt = boundingRect(contours.get(k));
            //除前景区域外其余全部为0


            Mat eleObj=mask.submat(rt);
            eleObj.setTo(new Scalar(255));
            // TODO: 2017/5/4,今天终于得到了轮廓

            //统计直方图
            Imgproc.calcHist(vecImg, vecChannel, mask, hist, vecHistSize, vecRange);
            //获取一个单通道图像的最大值最小值
            Core.MinMaxLocResult mLocResult = Core.minMaxLoc(hist);
            maxVal = mLocResult.maxVal;
            Core.multiply(hist, new Scalar(255 / maxVal), hist);
            //计算反向投影图
            calcBackProject(vecImg, vecChannel, hist, backProject, vecRange, 1);
            //camshift跟踪位置
            Rect search = rt;
            /*
			TermCriteria类表示迭代停止条件，COUNT表示满足迭代次数，EPS表示满足一定的准确率阈值
			*/
            RotatedRect rrt = Video.CamShift(backProject, search, new TermCriteria(TermCriteria.EPS + TermCriteria.COUNT, 10, 1));
            Rect rt2 = rrt.boundingRect();
            rt = rt2;


            //跟踪框画到视频上
            Core.rectangle(result, new Point(rt.x, rt.y), new Point(rt.x + rt.width, rt.y + rt.height), new Scalar(0, 255, 0), 2);
            // TODO: 2017/5/2 最终的处理结果,为什么出现这么多的空检。
        }
        /**
         * return 0 represent the camshift algorithm
         */
        return 0;
    }

    /**
     *
     * @param src     获取的4K图像的源矩阵
     * @param rowScale  X方向的缩放倍数
     * @param colScale  Y方向的缩放倍数
     * @return  缩放后的图像
     */
    public Mat resizeImg(Mat src, double rowScale, double colScale) {
        Mat dst=new Mat();
        Imgproc.resize(src,dst,new Size(),rowScale,colScale,INTER_AREA);
        return dst;
    }

}
