package ar.coderama.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class ModoEscaner { CODIGO, ETIQUETA }

/** Lee códigos de barras y QR con ML Kit, dentro del teléfono. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private class AnalizadorCodigos(private val onDetectado: (Barcode) -> Unit) : ImageAnalysis.Analyzer {
    private val lector = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128,
            )
            .build(),
    )
    private val detectado = AtomicBoolean(false)

    override fun analyze(imagen: ImageProxy) {
        val media = imagen.image
        if (media == null || detectado.get()) {
            imagen.close()
            return
        }
        val entrada = InputImage.fromMediaImage(media, imagen.imageInfo.rotationDegrees)
        lector.process(entrada)
            .addOnSuccessListener { codigos ->
                val c = codigos.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (c != null && detectado.compareAndSet(false, true)) onDetectado(c)
            }
            .addOnCompleteListener { imagen.close() }
    }
}

@Composable
fun PantallaEscaner(
    modo: ModoEscaner,
    codigoPrevio: String?,
    onCodigo: (String) -> Unit,
    onTextoEtiqueta: (String, String?) -> Unit,
    onVolver: () -> Unit,
) {
    val ctx = LocalContext.current
    var permiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso = it }
    LaunchedEffect(Unit) { if (!permiso) pedir.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permiso) {
            ContenidoCamara(modo, codigoPrevio, onCodigo, onTextoEtiqueta)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Coderama necesita la cámara para leer códigos y etiquetas.", color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = { pedir.launch(Manifest.permission.CAMERA) }) { Text("Dar permiso") }
            }
        }
        IconButton(
            onClick = onVolver,
            modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White) }
    }
}

@Composable
private fun ContenidoCamara(
    modo: ModoEscaner,
    codigoPrevio: String?,
    onCodigo: (String) -> Unit,
    onTextoEtiqueta: (String, String?) -> Unit,
) {
    val ctx = LocalContext.current
    val dueno = LocalLifecycleOwner.current
    var intento by remember { mutableIntStateOf(0) }
    var qr by remember { mutableStateOf<String?>(null) }
    var codigoVisto by remember { mutableStateOf(codigoPrevio) }
    var procesando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var linterna by remember { mutableStateOf(false) }
    val camara = remember { mutableStateOf<Camera?>(null) }
    val captura = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val ejecutor = remember { Executors.newSingleThreadExecutor() }

    val analizador = remember(intento) {
        AnalizadorCodigos { b ->
            b.rawValue?.let { valor ->
                val esProducto = b.format != Barcode.FORMAT_QR_CODE || (valor.all { it.isDigit() } && valor.length in 8..14)
                if (modo == ModoEscaner.ETIQUETA) {
                    if (esProducto) codigoVisto = valor
                } else if (esProducto) {
                    onCodigo(valor.filter { it.isDigit() }.ifEmpty { valor })
                } else {
                    qr = valor
                }
            }
        }
    }

    val vista = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(analizador) {
        val futuro = ProcessCameraProvider.getInstance(ctx)
        futuro.addListener({
            val proveedor = futuro.get()
            val vistaPrevia = Preview.Builder().build().also { it.setSurfaceProvider(vista.surfaceProvider) }
            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(ejecutor, analizador) }
            runCatching {
                proveedor.unbindAll()
                camara.value = if (modo == ModoEscaner.ETIQUETA) {
                    proveedor.bindToLifecycle(dueno, CameraSelector.DEFAULT_BACK_CAMERA, vistaPrevia, captura, analisis)
                } else {
                    proveedor.bindToLifecycle(dueno, CameraSelector.DEFAULT_BACK_CAMERA, vistaPrevia, analisis)
                }
            }.onFailure { error = "No se pudo abrir la cámara: ${it.message}" }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            runCatching { if (futuro.isDone) futuro.get().unbindAll() }
        }
    }
    DisposableEffect(Unit) { onDispose { ejecutor.shutdown() } }

    AndroidView(modifier = Modifier.fillMaxSize(), factory = { vista })

    // Marco guía
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val alto = if (modo == ModoEscaner.CODIGO) 170.dp else 420.dp
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .height(alto)
                .border(3.dp, Colores.amarillo, RoundedCornerShape(20.dp)),
        )
    }

    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val textoError = error
        val texto = when {
            textoError != null -> textoError
            modo == ModoEscaner.CODIGO -> "Apuntá al código de barras o QR del producto"
            else -> "Encuadrá la lista de ingredientes, la tabla nutricional o la etiqueta de energía" +
                (codigoVisto?.let { "\nCódigo detectado: $it" } ?: "")
        }
        Text(
            texto,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton(
                onClick = {
                    linterna = !linterna
                    camara.value?.cameraControl?.enableTorch(linterna)
                },
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
            ) {
                Icon(if (linterna) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, contentDescription = "Linterna", tint = Color.White)
            }
            if (modo == ModoEscaner.ETIQUETA) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (procesando) {
                        CircularProgressIndicator(color = Colores.verde)
                    } else {
                        IconButton(onClick = {
                            procesando = true
                            capturarTexto(captura, ctx, { t ->
                                procesando = false
                                if (t.isBlank()) error = "No se pudo leer texto. Probá con más luz y más cerca."
                                else onTextoEtiqueta(t, codigoVisto)
                            }, { m -> procesando = false; error = m })
                        }, modifier = Modifier.size(76.dp)) {
                            Icon(Icons.Filled.Camera, contentDescription = "Sacar foto", tint = Colores.verdeOscuro, modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
        }
    }

    qr?.let { contenido ->
        val esUrl = contenido.startsWith("http://") || contenido.startsWith("https://")
        val esFactura = contenido.contains("afip.gob.ar/fe/qr") || contenido.contains("arca.gob.ar/fe/qr")
        AlertDialog(
            onDismissRequest = { qr = null; intento++ },
            title = { Text(if (esFactura) "QR de factura electrónica" else "Código QR") },
            text = {
                Text(
                    if (esFactura) "Es el QR de un comprobante de ARCA (ex AFIP). Contiene los datos de la factura, no de un producto."
                    else "Este QR no identifica un producto. Contiene:\n\n$contenido",
                )
            },
            confirmButton = {
                if (esUrl) TextButton(onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contenido))) }
                    qr = null
                    intento++
                }) { Text("Abrir enlace", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { qr = null; intento++ }) { Text("Seguir escaneando") } },
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun capturarTexto(
    captura: ImageCapture,
    ctx: android.content.Context,
    onTexto: (String) -> Unit,
    onError: (String) -> Unit,
) {
    captura.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(imagen: ImageProxy) {
            val media = imagen.image
            if (media == null) {
                imagen.close()
                onError("No se pudo procesar la foto.")
                return
            }
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromMediaImage(media, imagen.imageInfo.rotationDegrees))
                .addOnSuccessListener { onTexto(it.text) }
                .addOnFailureListener { onError("Error al leer el texto: ${it.message}") }
                .addOnCompleteListener { imagen.close() }
        }

        override fun onError(e: ImageCaptureException) {
            onError("No se pudo sacar la foto: ${e.message}")
        }
    })
}
