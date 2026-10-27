package com.example.update
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
@Composable fun UpdateCheckCard(modifier:Modifier=Modifier, metadataLoader:(android.content.Context)->AppUpdater.Newer?={AppUpdater.newer(it)}, manualRefresh:(android.content.Context)->AppUpdater.Newer?={AppUpdater.newer(it,true)}) {
 val context=LocalContext.current
 var result by remember { mutableStateOf<AppUpdater.Newer?>(null) }
 var status by remember { mutableStateOf("Checking latest version...") }
 var reload by remember { mutableIntStateOf(0) }
 LaunchedEffect(reload) {
  status="Checking latest version..."
  try { result=withContext(Dispatchers.IO){if(reload>0)manualRefresh(context) else metadataLoader(context)};status=if(result==null)"You are on the latest version." else "Latest version available: ${result!!.name}" }
  catch(_:Exception){result=null;status="Unavailable: could not check the latest version."}
 }
 Card(modifier=modifier){ Column(Modifier.padding(16.dp)){
  Text(status)
  if(result!=null) Button(onClick={if(!AppUpdater.openEco(context))status="Could not open Eco or a browser. Try again."}){Text("Update")}
  OutlinedButton(onClick={reload++}){Text("Check again")}
  Text("Updates open in compatible Netra Ecosystem or this app's Eco website page. No update is downloaded here.")
 } }
}
@Composable fun AppUpdatePrompt(metadataLoader:(android.content.Context)->AppUpdater.Newer?={AppUpdater.newer(it)}) {
 var dismissed by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(false)}
 val context=LocalContext.current
 var newer by remember{mutableStateOf<AppUpdater.Newer?>(null)}
 var unavailable by remember{mutableStateOf(false)}
 LaunchedEffect(Unit){if(!dismissed)try{newer=withContext(Dispatchers.IO){metadataLoader(context)}}catch(_:Exception){}}
 if(!dismissed) newer?.let{r->AlertDialog(onDismissRequest={dismissed=true;newer=null},title={Text("Latest version available: ${r.name}")},text={Text(if(unavailable)"Could not open Eco or a browser. Try again." else "Open this app's update page in Netra Ecosystem or on the Eco website. Nothing downloads in this app.")},confirmButton={TextButton(onClick={if(AppUpdater.openEco(context))newer=null else unavailable=true}){Text("Update")}},dismissButton={TextButton(onClick={dismissed=true;newer=null}){Text("Later")}})}
}
