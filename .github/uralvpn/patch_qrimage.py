#!/usr/bin/env python3
# UralVPN: the QR scan screen can also take a picture (gallery, screenshot), not only the camera.
# - a button "Выбрать картинку с QR-кодом" under the title of the scan sheet;
# - the sheet stays open when the camera permission is denied (so the picture can still be chosen);
# - the picture is decoded with ZXing (4 binarizer variants, 2 sizes), the result goes the same way as a
#   camera scan (https links of the subscription, sing-box:// links, QRS).
# Usage: patch_qrimage.py <sfa-root>
import sys

root = sys.argv[1]
base = root + "/app/src/main/java/io/nekohasekai/sfa/compose/"


def swap(text, old, new, what, count=1):
    assert text.count(old) == count, "%s not found (%d != %d)" % (what, text.count(old), count)
    return text.replace(old, new)


# 1) view model: decode a picture
vm_path = base + "screen/qrscan/QRScanViewModel.kt"
vm = open(vm_path).read()
vm = swap(
    vm,
    "imageAnalysis?.setAnalyzer(analysisExecutor, imageAnalyzer!!)",
    "imageAnalyzer?.let { imageAnalysis?.setAnalyzer(analysisExecutor, it) }",
    "setAnalyzer calls",
    count=vm.count("imageAnalysis?.setAnalyzer(analysisExecutor, imageAnalyzer!!)"),
)
anchor = "    override fun onCleared() {\n"
vm = swap(vm, anchor, '''    // UralVPN: a QR code from a picture (gallery / screenshot)
    fun scanImage(uri: Uri) {
        val context = getApplication<Application>()
        analysisExecutor.execute {
            val text = runCatching { uralDecodeImage(context, uri) }.getOrNull()
            if (text == null) {
                _uiState.update {
                    it.copy(errorMessage = "QR-код на картинке не найден. Выберите чёткое изображение, на котором виден весь код.")
                }
            } else {
                handleScanSuccess(text)
            }
        }
    }

''' + anchor, "onCleared")
vm += '''
private fun uralDecodeImage(context: android.content.Context, uri: Uri): String? {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val hints = mapOf<com.google.zxing.DecodeHintType, Any>(com.google.zxing.DecodeHintType.TRY_HARDER to true)
    for (limit in listOf(2400, 1000)) {
        var sample = 1
        while (bounds.outWidth / sample > limit || bounds.outHeight / sample > limit) sample *= 2
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) } ?: continue
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val reader = com.google.zxing.qrcode.QRCodeReader()
        val variants = listOf(
            com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)),
            com.google.zxing.BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source)),
            com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source.invert())),
            com.google.zxing.BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source.invert())),
        )
        for (variant in variants) {
            try {
                return reader.decode(variant, hints).text
            } catch (_: Exception) {
                reader.reset()
            }
        }
    }
    return null
}
'''
open(vm_path, "w").write(vm)
print("view model: scanImage added")

# 2) scan sheet: the button, the picker, no close on denied permission
sh_path = base + "component/qr/QRScanSheet.kt"
sh = open(sh_path).read()
sh = swap(
    sh,
    """        if (isGranted) {
            hasPermission = true
        } else {
            onDismiss()
        }
""",
    """        if (isGranted) {
            hasPermission = true
        }
""",
    "permission result",
)
sh = swap(
    sh,
    "    LaunchedEffect(Unit) {\n        viewModel.resetQRSState()\n",
    """    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { picked ->
        picked?.let { viewModel.scanImage(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.resetQRSState()
""",
    "launched effect",
)
box = """            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (hasPermission) {"""
sh = swap(
    sh,
    box,
    """            androidx.compose.material3.OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
            ) {
                Text("Выбрать картинку с QR-кодом")
            }

""" + box,
    "camera box",
)
sh = swap(
    sh,
    "                if (uiState.isLoading) {\n",
    """                if (!hasPermission) {
                    Text(
                        text = "Нет доступа к камере. Выберите картинку с QR-кодом кнопкой выше.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }

                if (uiState.isLoading && hasPermission) {
""",
    "loading spinner",
)
open(sh_path, "w").write(sh)
print("scan sheet: picture button added")
