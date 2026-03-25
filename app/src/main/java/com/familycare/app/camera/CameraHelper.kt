package com.familycare.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.io.ByteArrayOutputStream

object CameraHelper {
    fun captureSnapshot(context: Context, onResult: (ByteArray?) -> Unit) {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val frontId = try {
            mgr.cameraIdList.firstOrNull { id ->
                mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }
        } catch (e: Exception) { null }

        if (frontId == null) { onResult(null); return }

        val thread = HandlerThread("CamSnap").also { it.start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(640, 480, android.graphics.ImageFormat.JPEG, 1)

        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage()
            val bytes = try {
                val buf = img.planes[0].buffer
                val data = ByteArray(buf.remaining()).also { buf.get(it) }
                rotateBitmap(data, 270f)
            } catch (e: Exception) { null } finally { img?.close() }
            onResult(bytes)
            reader.close()
            thread.quitSafely()
        }, handler)

        try {
            mgr.openCamera(frontId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    try {
                        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        }
                        cam.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                try { s.capture(req.build(), object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) { cam.close() }
                                }, handler) } catch (e: Exception) { cam.close(); onResult(null) }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) { cam.close(); onResult(null) }
                        }, handler)
                    } catch (e: Exception) { cam.close(); onResult(null) }
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close(); onResult(null) }
                override fun onError(cam: CameraDevice, e: Int) { cam.close(); onResult(null) }
            }, handler)
        } catch (e: Exception) { onResult(null); thread.quitSafely() }
    }

    private fun rotateBitmap(data: ByteArray, degrees: Float): ByteArray {
        return try {
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            val out = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        } catch (e: Exception) { data }
    }
}
