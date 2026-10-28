package com.example
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import com.example.update.AppUpdater
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class EcoMetadataHandoffTest {
 @Test fun metadataIgnoresAllArtifactUrls(){
  assertEquals("9.9",AppUpdater.parseNewer("""{"versionCode":999,"versionName":"9.9","apkUrl":"https://evil.invalid/payload.apk"}""",1)!!.name)
  assertNull(AppUpdater.parseNewer("""{"versionCode":1,"versionName":"1"}""",1))
 }
 @Test fun compatibleEcoGetsExactUri(){
  val c=RuntimeEnvironment.getApplication();val pm=Shadows.shadowOf(c.packageManager)
  pm.installPackage(PackageInfo().apply{packageName=AppUpdater.ECO_PACKAGE;longVersionCode=59})
  val eco=Intent(Intent.ACTION_VIEW,android.net.Uri.parse(AppUpdater.ECO_PAGE)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(AppUpdater.ECO_PACKAGE)
  pm.addResolveInfoForIntent(eco, android.content.pm.ResolveInfo().apply{activityInfo=android.content.pm.ActivityInfo().apply{packageName=AppUpdater.ECO_PACKAGE;name="MainActivity";exported=true}})
  assertTrue(AppUpdater.openEco(c))
  val i=Shadows.shadowOf(c).nextStartedActivity
  assertEquals(AppUpdater.ECO_PACKAGE,i.`package`);assertEquals(AppUpdater.ECO_PAGE,i.dataString)
  assertEquals(Intent.ACTION_VIEW,i.action);assertNull(i.extras)
 }
 private fun installBrowser(c:Application) {
  val info=android.content.pm.ResolveInfo().apply{filter=android.content.IntentFilter().apply{addAction(Intent.ACTION_VIEW);addCategory(Intent.CATEGORY_BROWSABLE);addCategory(Intent.CATEGORY_DEFAULT);addDataScheme("https")};activityInfo=android.content.pm.ActivityInfo().apply{packageName="org.browser.test";name="Browser";exported=true}}
  val i=Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE)
  Shadows.shadowOf(c.packageManager).addResolveInfoForIntent(i,info)
 }
 @Test fun oldEcoGetsWebsiteNotEcoHome(){
  val c=RuntimeEnvironment.getApplication();installBrowser(c)
  val generic=Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE)
  val own=android.content.pm.ResolveInfo().apply{filter=android.content.IntentFilter().apply{addAction(Intent.ACTION_VIEW);addCategory(Intent.CATEGORY_BROWSABLE);addCategory(Intent.CATEGORY_DEFAULT);addDataScheme("https")};activityInfo=android.content.pm.ActivityInfo().apply{packageName=c.packageName;name="MainActivity";exported=true}}
  val oldEco=android.content.pm.ResolveInfo().apply{filter=android.content.IntentFilter().apply{addAction(Intent.ACTION_VIEW);addCategory(Intent.CATEGORY_BROWSABLE);addCategory(Intent.CATEGORY_DEFAULT);addDataScheme("https")};activityInfo=android.content.pm.ActivityInfo().apply{packageName=AppUpdater.ECO_PACKAGE;name="MainActivity";exported=true}}
  Shadows.shadowOf(c.packageManager).addResolveInfoForIntent(generic,own)
  Shadows.shadowOf(c.packageManager).addResolveInfoForIntent(generic,oldEco)
  val ownLink=Intent(Intent.ACTION_VIEW,android.net.Uri.parse(AppUpdater.ECO_PAGE)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  Shadows.shadowOf(c.packageManager).addResolveInfoForIntent(ownLink,own)
  Shadows.shadowOf(c.packageManager).installPackage(PackageInfo().apply{packageName=AppUpdater.ECO_PACKAGE;longVersionCode=58})
  assertTrue(AppUpdater.openEco(c))
  val i=Shadows.shadowOf(c).nextStartedActivity
  assertEquals("org.browser.test",i.`package`);assertEquals(AppUpdater.ECO_PAGE,i.dataString)
 }
 @Test fun noEcoDoesNotLaunchRawApk(){
  val c=RuntimeEnvironment.getApplication()
  installBrowser(c)
  assertTrue(AppUpdater.openEco(c))
  val i=Shadows.shadowOf(c).nextStartedActivity
  if(i!=null){assertEquals(AppUpdater.ECO_PAGE,i.dataString);assertNotEquals(AppUpdater.ECO_PACKAGE,i.`package`)}
 }
 @Test fun noBrowserReturnsVisibleFailureSignal(){
  val c=RuntimeEnvironment.getApplication()
  assertFalse(AppUpdater.openEco(c))
 }
 @Test fun malformedAndOversizedMetadataFailClosed(){
  assertThrows(Exception::class.java){AppUpdater.parseNewer("invalid",1)}
  assertThrows(Exception::class.java){AppUpdater.parseNewer("""{"versionCode":999,"versionName":""}""",1)}
  assertThrows(IllegalArgumentException::class.java){AppUpdater.readMetadata(java.io.ByteArrayInputStream(ByteArray(131073)))}
  assertEquals(131072,AppUpdater.readMetadata(java.io.ByteArrayInputStream(ByteArray(131072))).size)
 }
 @Test fun deniedEcoLaunchFallsBackToExactBrowserPage(){
  val c=RuntimeEnvironment.getApplication();installBrowser(c)
  Shadows.shadowOf(c.packageManager).installPackage(PackageInfo().apply{packageName=AppUpdater.ECO_PACKAGE;longVersionCode=59})
  val launched=mutableListOf<Intent>()
  val wrapper=object:android.content.ContextWrapper(c){
   override fun startActivity(intent:Intent){
    if(intent.`package`==AppUpdater.ECO_PACKAGE)throw SecurityException("fixture denial")
    launched.add(intent)
   }
  }
  assertTrue(AppUpdater.openEco(wrapper))
  assertEquals("org.browser.test",launched.single().`package`)
  assertEquals(AppUpdater.ECO_PAGE,launched.single().dataString)
 }
 @Test fun unavailableEcoLaunchFallsBack(){
  val c=RuntimeEnvironment.getApplication();installBrowser(c)
  Shadows.shadowOf(c.packageManager).installPackage(PackageInfo().apply{packageName=AppUpdater.ECO_PACKAGE;longVersionCode=59})
  var browser=false
  val wrapper=object:android.content.ContextWrapper(c){
   override fun startActivity(intent:Intent){
    if(intent.`package`==AppUpdater.ECO_PACKAGE)throw android.content.ActivityNotFoundException("fixture removed")
    browser=intent.`package`=="org.browser.test"
   }
  }
  assertTrue(AppUpdater.openEco(wrapper));assertTrue(browser)
 }
}
