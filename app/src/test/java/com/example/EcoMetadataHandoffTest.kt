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
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class EcoMetadataHandoffTest {
 @Test fun metadataIgnoresAllArtifactUrls(){
  assertEquals("9.9",AppUpdater.parseNewer("""{"body":"versionCode: 999","tag_name":"v9.9","apkUrl":"https://evil.invalid/payload.apk"}""",1)!!.name)
  assertNull(AppUpdater.parseNewer("""{"body":"versionCode: 1","tag_name":"v1"}""",1))
 }
 @Test fun apiMetadataRejectsMissingDuplicateAndNonNumericCode(){
  for(json in listOf("""{"tag_name":"v9","body":"notes"}""", """{"tag_name":"v9","body":"versionCode: bad"}""", """{"tag_name":"v9","body":"versionCode: 999\nversionCode: 998"}""", """{"tag_name":"v9","body":"versionCode: 9999999999999999999999999"}""")) {
   assertThrows(Exception::class.java){AppUpdater.parseNewer(json,1)}
  }
  assertEquals(999,AppUpdater.parseNewer("""{"tag_name":"v9","body":"Notes\nversionCode: 999\r\nmore","assets":[{"browser_download_url":"https://evil.invalid/a.apk"}]}""",1)!!.code)
 }
 @Test fun cacheSharesLaunchChecksAndManualRefreshCanBypass(){
  val c=RuntimeEnvironment.getApplication();var calls=0
  val fetch={calls++;"""{"tag_name":"v9","body":"versionCode: 999"}"""}
  assertEquals(999,AppUpdater.cachedNewer(c,1,1000,false,fetch)!!.code)
  assertEquals(999,AppUpdater.cachedNewer(c,1,2000,false,fetch)!!.code);assertEquals(1,calls)
  assertNull(AppUpdater.cachedNewer(c,1000,3000,false,fetch));assertEquals(1,calls)
  AppUpdater.cachedNewer(c,1,4000,true,fetch);assertEquals(2,calls)
  AppUpdater.cachedNewer(c,1,4000+6*60*60*1000L,false,fetch);assertEquals(3,calls)
 }
 @Test fun failedChecksBackoffWithoutClaimingLatest(){
  val c=RuntimeEnvironment.getApplication();var calls=0
  val fail:()->String={calls++;throw java.io.IOException("offline")}
  assertThrows(java.io.IOException::class.java){AppUpdater.cachedNewer(c,1,1000,false,fail)}
  assertThrows(java.io.IOException::class.java){AppUpdater.cachedNewer(c,1,2000,false,fail)};assertEquals(1,calls)
  assertThrows(java.io.IOException::class.java){AppUpdater.cachedNewer(c,1,3000,true,fail)};assertEquals(2,calls)
 }
 @Test fun compatibleEcoGetsExactUri(){
  val c=RuntimeEnvironment.getApplication();val pm=Shadows.shadowOf(c.packageManager)
  pm.installPackage(PackageInfo().apply{packageName=AppUpdater.ECO_PACKAGE;longVersionCode=59})
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
  assertThrows(Exception::class.java){AppUpdater.parseNewer("""{"body":"versionCode: 999","tag_name":""}""",1)}
  assertThrows(IllegalArgumentException::class.java){AppUpdater.readMetadata(java.io.ByteArrayInputStream(ByteArray(131073)))}
  assertEquals(131072,AppUpdater.readMetadata(java.io.ByteArrayInputStream(ByteArray(131072))).size)
 }
}
