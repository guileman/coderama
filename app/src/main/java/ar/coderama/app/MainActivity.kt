package ar.coderama.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.coderama.app.ui.Colores
import ar.coderama.app.ui.ModoEscaner
import ar.coderama.app.ui.PantallaAjustes
import ar.coderama.app.ui.PantallaEscaner
import ar.coderama.app.ui.PantallaInicio
import ar.coderama.app.ui.PantallaOnboarding
import ar.coderama.app.ui.PantallaProducto
import ar.coderama.app.ui.TemaCoderama

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaCoderama {
                val ajustes by vm.ajustes.collectAsState()
                val a = ajustes
                if (a == null) {
                    Box(Modifier.fillMaxSize().background(Colores.fondo))
                    return@TemaCoderama
                }
                val nav = rememberNavController()
                val inicio = remember { if (a.onboardingHecho) "inicio" else "bienvenida" }
                NavHost(nav, startDestination = inicio) {
                    composable("bienvenida") {
                        PantallaOnboarding(vm) {
                            nav.navigate("inicio") { popUpTo("bienvenida") { inclusive = true } }
                        }
                    }
                    composable("inicio") {
                        PantallaInicio(
                            vm = vm,
                            onEscanear = { nav.navigate("escaner") },
                            onEtiqueta = { nav.navigate("etiqueta") },
                            onCodigo = { c -> vm.cargar(c); nav.navigate("producto") },
                            onAjustes = { nav.navigate("ajustes") },
                        )
                    }
                    composable("escaner") {
                        PantallaEscaner(
                            modo = ModoEscaner.CODIGO,
                            codigoPrevio = null,
                            onCodigo = { c ->
                                vm.cargar(c)
                                nav.navigate("producto") { popUpTo("escaner") { inclusive = true } }
                            },
                            onTextoEtiqueta = { _, _ -> },
                            onVolver = { nav.popBackStack() },
                        )
                    }
                    composable(
                        "etiqueta?codigo={codigo}",
                        arguments = listOf(navArgument("codigo") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    ) { entrada ->
                        val codigo = entrada.arguments?.getString("codigo")?.takeIf { it.isNotBlank() }
                        PantallaEscaner(
                            modo = ModoEscaner.ETIQUETA,
                            codigoPrevio = codigo,
                            onCodigo = {},
                            onTextoEtiqueta = { texto, cod ->
                                vm.analizarEtiqueta(texto, cod)
                                nav.navigate("producto") { popUpTo("etiqueta?codigo={codigo}") { inclusive = true } }
                            },
                            onVolver = { nav.popBackStack() },
                        )
                    }
                    composable("producto") {
                        PantallaProducto(
                            vm = vm,
                            onVolver = { nav.popBackStack() },
                            onLeerEtiqueta = { c -> nav.navigate("etiqueta?codigo=" + Uri.encode(c ?: "")) },
                            onEscanear = { nav.navigate("escaner") { popUpTo("inicio") } },
                            onAjustes = { nav.navigate("ajustes") },
                        )
                    }
                    composable("ajustes") {
                        PantallaAjustes(vm) { nav.popBackStack() }
                    }
                }
            }
        }
    }
}
