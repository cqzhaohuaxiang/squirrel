package com.android.squirrel.mouse





import android.content.Context
import android.view.MotionEvent
import com.android.squirrel.R
import org.rajawali3d.Object3D
import org.rajawali3d.lights.DirectionalLight
import org.rajawali3d.loader.LoaderOBJ
import org.rajawali3d.materials.Material
import org.rajawali3d.materials.methods.DiffuseMethod.Lambert
import org.rajawali3d.materials.methods.SpecularMethod.Phong
import org.rajawali3d.materials.textures.ATexture
import org.rajawali3d.materials.textures.Texture
import org.rajawali3d.math.Quaternion
import org.rajawali3d.math.vector.Vector3
import org.rajawali3d.primitives.Sphere
import org.rajawali3d.renderer.Renderer

//这个就只是显示一个飞机转呀转
class MouseRenderer(context : Context) : Renderer(context) {

    private var mEarth: Object3D? = null
    private var mRaptor: Object3D? = null
    private var backdrop: Object3D? = null
    private var light: DirectionalLight? = null

    override fun onOffsetsChanged(
        xOffset: Float,
        yOffset: Float,
        xOffsetStep: Float,
        yOffsetStep: Float,
        xPixelOffset: Int,
        yPixelOffset: Int
    ) {

    }

    override fun onTouchEvent(event: MotionEvent?) {}
    override fun initScene() {}




    public override fun onRender(elapsedTime: Long, deltaTime: Double) {
        super.onRender(elapsedTime, deltaTime)
        backdrop?.rotate(Vector3.Axis.Y, -0.05)
        backdrop?.rotate(Vector3.Axis.X, -0.05)

        mEarth?.rotate(Vector3.Axis.Y, -1.0)

    }


    fun setRaptorValues(x: Double, y: Double, z: Double) {
        mRaptor?.apply { setRotation(x, y, z) }
    }
    fun setRaptorValues(quaternion: Quaternion) {
        mRaptor?.apply { setRotation(quaternion) }
    }
    // mEarth?.isVisible = state//显示与隐藏模型
    fun modeVisible(){
        removeModel()
        raptor()
    }



    // 从场景中删除模型
    fun removeModel(){

        backdrop?.let {
            currentScene.removeChild(it)
            backdrop = null
        }
        light?.let {
            currentScene.removeLight(it)
            light = null
        }
        mEarth?.let {
            currentScene.removeChild(it)
            mEarth = null
        }
        mRaptor?.let {
            currentScene.removeChild(it)
            mRaptor = null
        }
    }






    private fun earth(){
        try {

            val materialStarry = Material()
            materialStarry.addTexture(
                Texture("mEarthStarry", R.raw.universe)
            )
            materialStarry.enableLighting(false)
            materialStarry.colorInfluence = 0f
            backdrop = Sphere(3f, 24, 24)
            // 反转球体以便内部可见
            backdrop!!.scale = Vector3(-3.0, -3.0, -3.0)
            backdrop!!.setMaterial(materialStarry)
            currentScene.addChild(backdrop)

            val material = Material()
            material.addTexture(
                Texture("mEarth", R.raw.earthtruecolor)
            )
            material.enableLighting(false) // 启用灯光
            material.diffuseMethod = Lambert() //漫射照明
//            material.specularMethod = Phong()//镜面高光
            material.colorInfluence = 0f
            mEarth = Sphere(0.5f, 24, 24)
            mEarth!!.setMaterial(material)
            currentScene.addChild(mEarth)// 将模型添加到场景中

        } catch (e: ATexture.TextureException) {
            e.printStackTrace()
        }

    }

    private fun raptor(){
        val objParser = LoaderOBJ(mContext.resources, mTextureManager, R.raw.f22)
        try {

            //添加灯光
            light = DirectionalLight(0.0, -1.0, -1.0)
            light!!.power = 1f
            currentScene.addLight(light)

            val material = Material()
            material.addTexture(
                Texture("colorimetrical", R.raw.earthtruecolor)
            )
            material.enableLighting(false)
            material.colorInfluence = 0f
            backdrop = Sphere(2f, 24, 24)
            // 反转球体以便内部可见
            backdrop!!.scale = Vector3(-2.0, -2.0, -2.0)
            backdrop!!.setMaterial(material)
            currentScene.addChild(backdrop)




            objParser.parse()
            mRaptor = objParser.parsedObject

            val scale = Vector3(0.1, 0.1, 0.1) // 缩小模型
            mRaptor!!.scale = scale // 应用缩放因子

            currentScene.addChild(mRaptor)

            val camouflage = Material()
            camouflage.enableLighting(true)
            camouflage.diffuseMethod = Lambert() //漫射照明
            camouflage.specularMethod = Phong()//镜面高光
            camouflage.addTexture(Texture("camouflage", R.raw.camouflage))
            camouflage.colorInfluence = 0f
            mRaptor!!.getChildByName("Camouflage").material = camouflage

            val cockpit = Material()
            cockpit.enableLighting(true)
            cockpit.diffuseMethod = Lambert() //漫射照明
            cockpit.specularMethod = Phong()//镜面高光
            cockpit.addTexture(Texture("cockpit", R.raw.cockpit))
//            cockpit.colorInfluence = 0f
            mRaptor!!.getChildByName("Cockpit").material = cockpit

            val exhaust = Material()
            exhaust.color = 0x0080ff
            exhaust.enableLighting(true)
            exhaust.diffuseMethod = Lambert()
            exhaust.specularMethod = Phong()
            exhaust.colorInfluence = 1f
            mRaptor!!.getChildByName("Exhaust").material = exhaust


        } catch (e: Exception) {
            e.printStackTrace()
        }


    }


}