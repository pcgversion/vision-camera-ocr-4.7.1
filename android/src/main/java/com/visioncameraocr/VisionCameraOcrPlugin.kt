package com.visioncameraocr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.media.Image
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.common.internal.ImageConvertUtils
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import android.graphics.Matrix

import androidx.camera.core.ImageProxy
import com.mrousavy.camera.frameprocessors.Frame
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin
import com.mrousavy.camera.frameprocessors.VisionCameraProxy
import com.facebook.react.bridge.ReactApplicationContext

// OpenCV imports for image brightness and sharpness
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import android.util.Log
import org.opencv.core.MatOfDouble
import org.opencv.android.OpenCVLoader
import org.opencv.core.Scalar
import org.opencv.core.Core
import org.opencv.core.Size

import java.io.IOException
import kotlin.text.format
import android.os.Environment

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


import android.view.Display
import android.view.Surface
import android.view.WindowManager

class VisionCameraOcrPlugin(proxy: VisionCameraProxy, options: Map<String, Any>?): FrameProcessorPlugin() {
    private val _context:ReactApplicationContext = proxy.context
    companion object {
        init {
            if (OpenCVLoader.initDebug()) {
                Log.d("OpenCV", "OpenCV library loaded successfully")
            } else {
                Log.e("OpenCV", "Failed to load OpenCV library")
            }
        }
    }

    private fun getBlockArray(blocks: MutableList<Text.TextBlock>): List<Map<String, Any?>> {
        val blockList = mutableListOf<Map<String, Any?>>()
        for (block in blocks) {
            val blockMap = mutableMapOf<String, Any?>()
            blockMap["text"] = block.text
            blockMap["recognizedLanguages"] = getRecognizedLanguages(block.recognizedLanguage)
            blockMap["cornerPoints"] = block.cornerPoints?.let { getCornerPoints(it) }
            blockMap["frame"] = getFrame(block.boundingBox)
            blockMap["lines"] = getLineArray(block.lines)
            blockList.add(blockMap)
        }
        return blockList
    }

    private fun getLineArray(lines: MutableList<Text.Line>): List<Map<String, Any?>> {
        val lineList = mutableListOf<Map<String, Any?>>()
        for (line in lines) {
            val lineMap = mutableMapOf<String, Any?>()
            lineMap["text"] = line.text
            lineMap["recognizedLanguages"] = getRecognizedLanguages(line.recognizedLanguage)
            lineMap["cornerPoints"] = line.cornerPoints?.let { getCornerPoints(it) }
            lineMap["frame"] = getFrame(line.boundingBox)
            lineMap["elements"] = getElementArray(line.elements)
            lineMap["confidence"] = line.confidence.toDouble()
            lineMap["angle"] = line.angle.toDouble()
            lineList.add(lineMap)
        }
        return lineList
    }

    private fun getElementArray(elements: MutableList<Text.Element>): List<Map<String, Any?>> {
        val elementList = mutableListOf<Map<String, Any?>>()
        for (element in elements) {
            val elementMap = mutableMapOf<String, Any?>()
            elementMap["text"] = element.text
            elementMap["cornerPoints"] = element.cornerPoints?.let { getCornerPoints(it) }
            elementMap["frame"] = getFrame(element.boundingBox)
            elementList.add(elementMap)
        }
        return elementList
    }

    private fun getRecognizedLanguages(recognizedLanguage: String): List<String> {
        val recognizedLanguages = mutableListOf<String>()
        recognizedLanguages.add(recognizedLanguage)
        return recognizedLanguages
    }

    private fun getCornerPoints(points: Array<Point>): List<Map<String, Int>> {
        val cornerPointList = mutableListOf<Map<String, Int>>()
        for (point in points) {
            val pointMap = mutableMapOf<String, Int>()
            pointMap["x"] = point.x
            pointMap["y"] = point.y
            cornerPointList.add(pointMap)
        }
        return cornerPointList
    }

    private fun getFrame(boundingBox: Rect?): Map<String, Any?> {
        val frameMap = mutableMapOf<String, Any?>()
        if (boundingBox != null) {
            frameMap["x"] = boundingBox.exactCenterX().toDouble()
            frameMap["y"] = boundingBox.exactCenterY().toDouble()
            frameMap["width"] = boundingBox.width()
            frameMap["height"] = boundingBox.height()
            frameMap["boundingCenterX"] = boundingBox.centerX()
            frameMap["boundingCenterY"] = boundingBox.centerY()
        }
        return frameMap
    }

    /*override fun callback(frame: ImageProxy, params: Array<Any>): Any? {

        val result = WritableNativeMap()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        @SuppressLint("UnsafeOptInUsageError")
        val mediaImage: Image? = frame.getImage()

        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, frame.imageInfo.rotationDegrees)
            val task: Task<Text> = recognizer.process(image)
            try {
                val text: Text = Tasks.await<Text>(task)
                result.putString("text", text.text)
                result.putArray("blocks", getBlockArray(text.textBlocks))
            } catch (e: Exception) {
                return null
            }
        }

        val data = WritableNativeMap()
        data.putMap("result", result)
        return data
    }*/

    override fun callback(frame: Frame, params: Map<String, Any>?): Any? {
        val finalResponse = mutableMapOf<String, Any?>()
        val resultData = mutableMapOf<String, Any?>() // For data to be nested under "result" key
        val surfaceRotation = getDeviceSurfaceRotation(_context)
        //Log.d("VisionCameraOCR", "Frame orientation: ${frame.orientation} ${surfaceRotation}")
        @SuppressLint("UnsafeOptInUsageError")
        val mediaImage: Image? = frame.image
        //Log.d("VisionCameraOcr", "Frame: orientation ${frame.orientation}, isValid ${frame.isValid}, width ${frame.width}, height ${frame.height}, image format ${mediaImage?.format}")
        var newRotation = frame.imageProxy.imageInfo.rotationDegrees
        //Log.d("OCR Rotation:", "${newRotation} ${surfaceRotation}")
        if(surfaceRotation == 0)
            newRotation = 90
        if(surfaceRotation == 1)
            newRotation = 0
        if(surfaceRotation == 3)
            newRotation = 180
        
        if (mediaImage != null) {
            //val image = InputImage.fromMediaImage(mediaImage, newRotation)
            //val bitmap = ImageConvertUtils.getInstance().getUpRightBitmap(image)
            //saveBitmapToFile(_context, bitmap, "test_image2.jpg")
            val bitmap: Bitmap? = convertImageProxyToBitmap(frame.imageProxy, surfaceRotation)
            if (bitmap != null) {
                try {
                    val brightness = calculateBrightnessScore(bitmap)
                    val sharpness = calculateSharpnessScore(bitmap)
                    
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    // The bitmap from convertImageProxyToBitmap is already rotated to be upright.
                    // So, the rotationDegrees for InputImage.fromBitmap should be 0.
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val task: Task<Text> = recognizer.process(inputImage)
                    try {
                        val text: Text = Tasks.await(task)
                        resultData["text"] = text.text
                        resultData["blocks"] = getBlockArray(text.textBlocks)
                        resultData["brightness"] = brightness
                        resultData["sharpness"] = sharpness
                        finalResponse["result"] = resultData
                    } catch (e: Exception) {
                        Log.e("VisionCameraOcr", "Error processing text", e)
                        finalResponse["error"] = "OCR processing failed: ${e.message}"
                    }
                } catch (e: Exception) {
                    Log.e("VisionCameraOcr", "Error during bitmap processing (e.g. brightness/sharpness calculation)", e)
                    finalResponse["error"] = "Bitmap processing error: ${e.message}"
                } finally {
                    //bitmap.recycle() // Recycle bitmap after use
                }
            /*} else {
                Log.e("VisionCameraOcr", "Bitmap conversion failed. Image format: ${mediaImage.format}")
                finalResponse["error"] = "Bitmap conversion failed"*/
            }
        } else {
            Log.e("VisionCameraOcr", "MediaImage is null.")
            finalResponse["error"] = "MediaImage is null"
        }

        return finalResponse
    }

//    private fun convertImageProxyToBitmap(imageProxy: ImageProxy, surfaceRotation: Int): Bitmap? {
//        val image = imageProxy.image ?: return null
//        val yBuffer = image.planes[0].buffer // Y
//        val uBuffer = image.planes[1].buffer // U
//        val vBuffer = image.planes[2].buffer // V
//
//        val ySize = yBuffer.remaining()
//        val uSize = uBuffer.remaining()
//        val vSize = vBuffer.remaining()
//
//        val nv21 = ByteArray(ySize + uSize + vSize)
//        yBuffer.get(nv21, 0, ySize)
//        vBuffer.get(nv21, ySize, vSize)
//        uBuffer.get(nv21, ySize + vSize, uSize)
//
//        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
//        val out = ByteArrayOutputStream()
//        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
//        val byteArray = out.toByteArray()
//        val originalBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
//
//
//        // Rotate the bitmap based on the rotation degrees
//        Log.d("convertImageProxyToBitmap", "Rotation: ${imageProxy.imageInfo.rotationDegrees}")
//        var rotationDegrees =  imageProxy.imageInfo.rotationDegrees
//
//
//        if(surfaceRotation == 1)
//            rotationDegrees = 0
//        if(surfaceRotation == 3)
//            rotationDegrees = 180
//
//        val matrix = Matrix()
//        matrix.postRotate(rotationDegrees.toFloat())
//
//            val rotatedBitmap = Bitmap.createBitmap(
//                originalBitmap,
//            0,
//            0,
//                originalBitmap.width,
//                originalBitmap.height,
//            matrix,
//            true
//        )
//        // Recycle the original bitmap if it's different from the rotated one and no longer needed
//        if (originalBitmap != rotatedBitmap) {
//            originalBitmap.recycle()
//        }
//
//
//
//        // --- Save the rotated bitmap to a temporary file ---
//
//        //saveBitmapToFile(_context, rotatedBitmap, "test_image.jpg")
//
//        // --- End saving ---
//
//        return rotatedBitmap
//    }

    private fun convertImageProxyToBitmap(
        imageProxy: ImageProxy,
        surfaceRotation: Int
    ): Bitmap? {
        val image = imageProxy.image ?: return null
        val width = imageProxy.width
        val height = imageProxy.height

        // Convert YUV_420_888 to NV21
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        // Convert NV21 byte array to OpenCV Mat
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)

        val rgbMat = Mat()
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)

        // Determine rotation
        var rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Optional: override with sensor orientation if needed
        if (surfaceRotation == 1) rotationDegrees = 0
        if (surfaceRotation == 3) rotationDegrees = 180

        val rotatedMat = Mat()
        when (rotationDegrees) {
            90 -> Core.rotate(rgbMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(rgbMat, rotatedMat, Core.ROTATE_180)
            270 -> Core.rotate(rgbMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> rgbMat.copyTo(rotatedMat)
        }

        // Convert rotated Mat to Bitmap
        val bitmap = Bitmap.createBitmap(rotatedMat.cols(), rotatedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rotatedMat, bitmap)

        // Release memory
        yuvMat.release()
        rgbMat.release()
        rotatedMat.release()

        return bitmap
    }

private fun saveBitmapToFile(context: Context, bitmap: Bitmap, baseFilename: String? = null) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date())
        val actualFilename = baseFilename

        // Choose storage location:
        // 1. App-specific cache directory (recommended for temporary files)
        val cacheDir = context.cacheDir
        val file = File(cacheDir, actualFilename)

        // 2. App-specific files directory (for files you want to keep longer but private to app)
        // val filesDir = context.getExternalFilesDir(null) // Or context.filesDir for internal
        // val file = File(filesDir, actualFilename)

        // 3. Public directory (requires more permissions and careful handling of Scoped Storage on Android 10+)
        //    For temporary images, app-specific storage is usually better.
        // val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        // val file = File(publicDir, actualFilename)
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !publicDir.exists()) {
        // publicDir.mkdirs()
        // }

        try {
            FileOutputStream(file).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream) // Adjust quality as needed
                outStream.flush()
                Log.d("VisionCameraOCR", "Bitmap saved successfully to: ${file.absolutePath}")
            }
        } catch (e: IOException) {
            Log.e("VisionCameraOCR", "Error saving bitmap to file", e)
        }
    }
        
    //Open CV Changes
    fun calculateBrightnessScore(bitmap: Bitmap): Double {
        val brightness = calculateBrightness(bitmap)
        return brightness
    }

    fun calculateBrightness(bitmap: Bitmap): Double {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(grayMat, mean, stddev)

        mat.release()
        grayMat.release()

        return mean.get(0, 0)[0]
    }

    //Higher scores mean sharper images,
    private fun calculateSharpnessScore(bitmap: Bitmap): Double {

        val mat = Mat()
        try {
            Utils.bitmapToMat(bitmap, mat)

            // Convert to grayscale
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)

            // Apply Laplacian operator to detect edges
            val laplacianMat = Mat()
            Imgproc.Laplacian(mat, laplacianMat, CvType.CV_64F)

            // Compute the standard deviation of the Laplacian result
            val meanStdDev = MatOfDouble()
            val stdDevMat = MatOfDouble()

            org.opencv.core.Core.meanStdDev(laplacianMat, meanStdDev, stdDevMat)

            val stddev = stdDevMat.get(0, 0)?.get(0) ?: 0.0

            // Release resources
            mat.release()
            laplacianMat.release()
            meanStdDev.release()
            stdDevMat.release()

            return stddev
        } catch (e: Exception) {
            Log.e("openCV", "Error while calculating sharpness score", e)
            mat.release() // Ensure release even in error case
            throw e
        }
    }

    /**
     * Gets the current rotation of the device's default display surface.
     *
     * @param context The Android application or activity context.
     * @return An integer representing the rotation. This will be one of:
     *         <ul>
     *           <li>{@link android.view.Surface#ROTATION_0} (no rotation, natural orientation)</li>
     *           <li>{@link android.view.Surface#ROTATION_90} (rotated 90 degrees clockwise)</li>
     *           <li>{@link android.view.Surface#ROTATION_180} (rotated 180 degrees)</li>
     *           <li>{@link android.view.Surface#ROTATION_270} (rotated 270 degrees clockwise)</li>
     *         </ul>
     *         Returns -1 if the context is null or the WindowManager service cannot be accessed.
     */
    fun getDeviceSurfaceRotation(context: Context?): Int {
        if (context == null) {
            System.err.println("Error: Context cannot be null to get device surface rotation.")
            return -1 // Or throw an IllegalArgumentException
        }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE)  as? WindowManager
        return windowManager?.defaultDisplay?.rotation ?: run {
                System.err.println("Error: Could not retrieve WindowManager or Display service.")
                // For Android-specific logging, consider:
                // Log.e("Frame", "Error: Could not retrieve WindowManager or Display service.")
                -1
            }
    }
}