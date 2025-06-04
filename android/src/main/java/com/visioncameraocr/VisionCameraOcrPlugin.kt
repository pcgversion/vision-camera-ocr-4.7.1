package com.visioncameraocr

import android.annotation.SuppressLint
import android.graphics.Point
import android.graphics.Rect
import android.media.Image
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
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


class VisionCameraOcrPlugin(proxy: VisionCameraProxy, options: Map<String, Any>?): FrameProcessorPlugin() {

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

        @SuppressLint("UnsafeOptInUsageError")
        val mediaImage: Image? = frame.image
        //Log.d("VisionCameraOcr", "Frame: orientation ${frame.orientation}, isValid ${frame.isValid}, width ${frame.width}, height ${frame.height}, image format ${mediaImage?.format}")

        if (mediaImage != null) {
            val bitmap = convertImageProxyToBitmap(frame.imageProxy)
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
                    bitmap.recycle() // Recycle bitmap after use
                }
            } else {
                Log.e("VisionCameraOcr", "Bitmap conversion failed. Image format: ${mediaImage.format}")
                finalResponse["error"] = "Bitmap conversion failed"
            }
        } else {
            Log.e("VisionCameraOcr", "MediaImage is null.")
            finalResponse["error"] = "MediaImage is null"
        }

        
        return finalResponse
    }

    private fun convertImageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val byteArray = out.toByteArray()
        val originalBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

        // Rotate the bitmap based on the rotation degrees
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())

        return Bitmap.createBitmap(
            originalBitmap, 
            0, 
            0, 
            originalBitmap.width, 
            originalBitmap.height, 
            matrix, 
            true
        )
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
}