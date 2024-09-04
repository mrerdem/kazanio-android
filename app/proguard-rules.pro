# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

-keep public class * extends com.creadeep.kazanio.TicketDbHelper
-keep public class * extends com.creadeep.kazanio.Ticket
-keep public class * extends com.creadeep.kazanio.network.ServerUtils
-keep public class * extends com.creadeep.kazanio.data.TicketRepository
-keep public class * extends com.creadeep.kazanio.data.TicketDatabase
-keep public class * extends com.creadeep.kazanio.network.KazanioUploadResponse
-keep public class * extends com.creadeep.kazanio.data.TicketEntity
-keep public class * extends com.creadeep.kazanio.data.TicketViewModel
-keep public class * extends com.creadeep.kazanio.ScanFragment
-keep public class * extends com.creadeep.kazanio.data.TicketListViewModel
-keep public class * extends com.creadeep.kazanio.MainActivity
-keep public class com.creadeep.kazanio.SettingsRemindersFragment

#-printusage /home/pc/Documents/AndroidStudioProjects/Kazanio/proGuardDeletedClasses.txt

