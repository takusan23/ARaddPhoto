package io.github.takusan23.araddphoto

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var arFragment: ArFragment

    val REQUEST_CODE = 855

    var hitResult: HitResult? = null
    var plane: Plane? = null
    var motionEvent: MotionEvent? = null
    var imageView: ImageView? = null
    var uri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //条件満たしてなければActivity終了させる
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return
        }
        setContentView(R.layout.activity_main)
        //ArFragment取得
        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment

        arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            Toast.makeText(this,R.string.error_select_image,Toast.LENGTH_SHORT).show()
        }

        //ノッチ領域に展開
        //ステータスバー透明化＋タイトルバー非表示＋ノッチ領域にも侵略
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        supportActionBar?.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrib = window.attributes
            attrib.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        //SAFだす
        button.setOnClickListener {
            //SAF表示
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_CODE)
            arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
                //いれておく
                this.hitResult = hitResult
                this.plane = plane
                this.motionEvent = motionEvent
                ViewRenderable.builder()
                        .setView(this, ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(300, 300) })
                        .build()
                        .thenAccept { t: ViewRenderable? ->
                            // Create the Anchor.
                            val anchor = hitResult?.createAnchor()
                            val anchorNode = AnchorNode(anchor)
                            anchorNode.setParent(arFragment.arSceneView.scene)
                            // Create the transformable andy and add it to the anchor.
                            val node = TransformableNode(arFragment.transformationSystem)
                            node.setParent(anchorNode)
                            node.renderable = t
                            node.select()
                            imageView = (t?.view as ImageView)
                            Glide.with(imageView!!)
                                .load(uri)
                                .into(imageView!!)
                        }
                        .exceptionally {
                            //読み込み失敗
                            it.printStackTrace()
                            Toast.makeText(this, "読み込みに失敗しました。", Toast.LENGTH_LONG).show()
                            null
                        }
            }
        }

        //撮影ボタン
        shatter_button.setOnClickListener {
            //PixelCopy APIを利用する。のでOreo以降じゃないと利用できません。
            val bitmap = Bitmap.createBitmap(
                arFragment.view?.width ?: 100,
                arFragment.view?.height ?: 100,
                Bitmap.Config.ARGB_8888
            )
            val intArray = IntArray(2)
            arFragment.view?.getLocationInWindow(intArray)
            try {
                PixelCopy.request(
                    arFragment.arSceneView as SurfaceView, //SurfaceViewを継承してるらしい。windowだと真っ暗なので注意！
                    Rect(
                        intArray[0],
                        intArray[1],
                        intArray[0] + (arFragment.view?.width ?: 0),
                        intArray[1] + (arFragment.view?.height ?: 0)
                    ),
                    bitmap,
                    { copyResult: Int ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            //成功時
                            //ここのフォルダは自由に使っていい場所（サンドボックス）
                            val mediaFolder = externalMediaDirs.first()
                            //写真ファイル作成
                            val file = File("${mediaFolder.path}/${System.currentTimeMillis()}.jpg")
                            //Bitmap保存
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, file.outputStream())
                            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
                        }
                    },
                    Handler()
                )
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "失敗しました。", Toast.LENGTH_LONG).show()
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            uri = data?.data
        }
    }

    fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        val MIN_OPENGL_VERSION = 3.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Toast.makeText(activity, "SceneformにはAndroid N以降が必要です。", Toast.LENGTH_LONG).show()
            activity.finish()
            return false
        }
        val openGlVersionString = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.glEsVersion
        if (java.lang.Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Toast.makeText(activity, "SceneformにはOpen GL 3.0以降が必要です。", Toast.LENGTH_LONG).show()
            activity.finish()
            return false
        }
        return true
    }

}
