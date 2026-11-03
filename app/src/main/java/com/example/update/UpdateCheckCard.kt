package com.example.update
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/** Update card stays hidden unless a newer version is verified (owner rule, 3 Nov). No static explainer text. */
@Composable fun UpdateCheckCard(modifier:Modifier=Modifier, metadataLoader:(android.content.Context)->AppUpdater.Newer?={AutomaticUpdateNotice.check(it)}) {
 val context=LocalContext.current
 var result by remember { mutableStateOf<AppUpdater.Newer?>(null) }
 var openFailed by remember { mutableStateOf(false) }
 LaunchedEffect(Unit) {
  try { result=withContext(Dispatchers.IO){metadataLoader(context)} }
  catch(_:Exception){result=null}
 }
 val newer=result?:return
 Card(modifier=modifier){ Column(Modifier.padding(16.dp)){
  Text("Latest version available: ${newer.name}")
  if(openFailed) Text("Could not open Eco or a browser. Try again.")
  Button(onClick={if(!AppUpdater.openEco(context))openFailed=true}){Text("Update")}
 } }
}
@Composable fun AppUpdatePrompt(metadataLoader:(android.content.Context)->AppUpdater.Newer?={AutomaticUpdateNotice.check(it)}) {
 var dismissed by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(false)}
 val context=LocalContext.current
 var newer by remember{mutableStateOf<AppUpdater.Newer?>(null)}
 var unavailable by remember{mutableStateOf(false)}
 LaunchedEffect(Unit){if(!dismissed)try{newer=withContext(Dispatchers.IO){metadataLoader(context)}}catch(_:Exception){}}
 if(!dismissed) newer?.takeUnless{AutomaticUpdateNotice.wasDismissed(context,it.code)}?.let{r->AlertDialog(onDismissRequest={dismissed=true;newer?.let{AutomaticUpdateNotice.dismiss(context,it.code)};newer=null},title={Text("Latest version available: ${r.name}")},text={Text(if(unavailable)"Could not open Eco or a browser. Try again." else "Open this app's update page in Netra Ecosystem or on the Eco website. Nothing downloads in this app.")},confirmButton={TextButton(onClick={if(AppUpdater.openEco(context))newer=null else unavailable=true}){Text("Update")}},dismissButton={TextButton(onClick={dismissed=true;newer?.let{AutomaticUpdateNotice.dismiss(context,it.code)};newer=null}){Text("Later")}})}
}
