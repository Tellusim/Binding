// MIT License
// 
// Copyright (C) 2018-2024, Tellusim Technologies Inc. https://tellusim.com/
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.main

import com.tellusim.*

import kotlin.math.*

import java.nio.ByteOrder
import java.nio.ByteBuffer

/*
 */
fun create_mesh(steps: Vector2i, radius: Vector2f, texcoord: Float): Mesh {
	
	// create mesh
	val mesh = Mesh()
	
	// create vertices
	val num_vertices = (steps.x + 1) * (steps.y + 1)
	val positions = MeshAttribute(MeshAttribute.Type.Position, Format.RGBf32, num_vertices)
	val normals = MeshAttribute(MeshAttribute.Type.Normal, Format.RGBf32, num_vertices)
	val tangents = MeshAttribute(MeshAttribute.Type.Tangent, Format.RGBAf32, num_vertices)
	val texcoords = MeshAttribute(MeshAttribute.Type.TexCoord, Format.RGf32, num_vertices)
	
	var vertex = 0
	val isteps = Vector2f(1.0f) / Vector2f(steps)
	val aspect = radius.x / radius.y
	for(j in 0 .. steps.y) {
		val ty = j * isteps.y
		val z = -cos(ty * Base.Pi2 - Base.Pi05)
		val r = sin(ty * Base.Pi2 - Base.Pi05)
		for(i in 0 .. steps.x) {
			val tx = i * isteps.x
			val x = -sin(tx * Base.Pi2)
			val y = cos(tx * Base.Pi2)
			positions.set(vertex, Vector3f(x * (r * radius.y + radius.x), y * (r * radius.y + radius.x), z * radius.y))
			normals.set(vertex, Vector3f(x * r, y * r, z))
			tangents.set(vertex, Vector4f(-y, x, 0.0f, 1.0f))
			texcoords.set(vertex, Vector2f(tx * aspect, ty) * texcoord)
			vertex++
		}
	}
	
	val basis = MeshAttribute(MeshAttribute.Type.Basis, Format.RGBAu32, num_vertices)
	basis.packAttributes(normals, tangents, Format.RGBAf16)
	
	// create indices
	val num_indices = steps.x * steps.y * 4
	val indices_format = if(num_vertices < Base.Maxu16) Format.Ru16 else Format.Ru32
	val indices = MeshIndices(MeshIndices.Type.Quadrilateral, indices_format, num_indices)
	
	var index = 0
	for(j in 0 .. steps.y - 1) {
		for(i in 0 .. steps.x - 1) {
			vertex = (steps.x + 1) * j + i
			indices.set(index, vertex, vertex + 1, vertex + steps.x + 2, vertex + steps.x + 1)
			index += 4
		}
	}
	
	// create geometry
	val geometry = MeshGeometry(mesh)
	geometry.addAttribute(positions, indices)
	geometry.addAttribute(basis, indices)
	geometry.addAttribute(normals, indices)
	geometry.addAttribute(texcoords, indices)
	
	// geometry bounds
	val hsize = Vector3f(Vector2f(radius.x + radius.y), radius.y)
	geometry.setBoundBox(BoundBoxf(-hsize, hsize))
	
	// create bounds
	mesh.createBounds()
	
	return mesh
}

/*
 */
fun create_image(size: Int, frame: Int): Image {
	
	// create image
	val image = Image()
	image.create2D(Format.RGBAu8n, size)
	
	// create sampler
	val sampler = ImageSampler(image, Slice())
	
	// fill image
	var color = ImageColor(255)
	for(y in 0 .. size - 1) {
		for(x in 0 .. size - 1) {
			val v = (((x - (frame xor y)) xor (y + (frame xor x))) and 255).toFloat() / 63.0f
			color.setR((cos(Base.Pi * 1.0f + v) * 127.5f + 127.5f).toInt())
			color.setG((cos(Base.Pi * 0.5f + v) * 127.5f + 127.5f).toInt())
			color.setB((cos(Base.Pi * 0.0f + v) * 127.5f + 127.5f).toInt())
			sampler.set2D(x, y, color)
		}
	}
	
	return image
}

/*
 */
fun main(args: Array<String>) {
	
	// load library
	Base.loadDebug()
	
	// create app
	val app = App(args)
	if(!app.create(Platform.Any)) return
	
	// create window
	val window = Window(app.platform, app.device)
	if(!window.isValidPtr()) return
	
	window.setSize(app.width, app.height)
	window.setCloseClickedCallback(object: Window.CloseClickedCallback() {
		override fun run() { window.stop() }
	})
	window.setKeyboardPressedCallback(object: Window.KeyboardPressedCallback() {
		override fun run(key: Int, code: Int) {
			if(key == Window.Key.Esc.value) window.stop()
			if(key == Window.Key.F12.value) {
				val image = Image()
				if(window.grab(image) && image.save("screenshot.png")) {
					Log.print(Log.Level.Message, "Screenshot\n")
				}
			}
		}
	})
	
	val title = window.platformName + " Tellusim::Kotlin"
	val flags = Window.Flags.DefaultFlags.or(Window.Flags.VerticalSync)
	if(!window.create(title, flags) || !window.setHidden(false)) return
	
	// create device
	val device = Device(window)
	if(!device.isValidPtr()) return
	
	// device features
	Log.printf(Log.Level.Message, "Features:\n%s\n", device.features)
	Log.printf(Log.Level.Message, "Device: %s\n", device.name)
	
	// build info
	Log.printf(Log.Level.Message, "Build: %s\n", App.getBuildInfo())
	
	// create target
	val target = device.createTarget(window)
	if(!target.isValidPtr()) return
	
	////////////////////////////////
	// core test
	////////////////////////////////
	
	val blob = Blob()
	blob.writeString(title)
	
	blob.seek(0)
	Log.printf(Log.Level.Message, "Stream: %s\n", blob.readString())
	
	////////////////////////////////
	// platform test
	////////////////////////////////
	
	// create pipeline
	val pipeline = device.createPipeline()
	pipeline.setUniformMask(0, Shader.Mask.Fragment)
	pipeline.setColorFormat(window.colorFormat)
	pipeline.setDepthFormat(window.depthFormat)
	pipeline.addAttribute(Pipeline.Attribute.Position, Format.RGf32, 0, 0, 8)
	if(!pipeline.loadShaderGLSL(Shader.Type.Vertex, "main.shader", "VERTEX_SHADER=1")) return
	if(!pipeline.loadShaderGLSL(Shader.Type.Fragment, "main.shader", "FRAGMENT_SHADER=1")) return
	if(!pipeline.create()) return
	
	// geometry vertices
	val vertices = arrayOf( Vector2f(3.0f, -1.0f), Vector2f(-1.0f, -1.0f), Vector2f(-1.0f, 3.0f) )
	
	// geometry indices
	val indices = shortArrayOf( 0, 1, 2 )
	
	////////////////////////////////
	// interface test
	////////////////////////////////
	
	// create canvas
	val canvas = Canvas()
	
	// create root controls
	val root = ControlRoot(canvas, true)
	root.fontSize = 24
	
	// create rect
	val rect = ControlRect(root.ref(), CanvasElement.Mode.Texture)
	rect.setAlign(Control.Align.Expand)
	rect.setFullscreen(true)
	
	// create dialog
	val dialog = ControlDialog(root.ref(), 1, 8.0f, 8.0f)
	dialog.setUpdatedCallback(object: ControlDialog.UpdatedCallback() {
		override fun run(dialog: ControlDialog) {
			val x = dialog.positionX.toInt()
			val y = dialog.positionY.toInt()
			val width = dialog.width.toInt()
			val height = dialog.height.toInt()
			Log.printf(Log.Level.Message, "Dialog Updated %d %d %dx%d\n", x, y, width, height)
		}
	})
	dialog.setAlign(Control.Align.Center)
	dialog.setSize(240.0f, 180.0f)
	
	// create text
	val text = ControlText(dialog.ref(), title)
	text.setAlign(Control.Align.CenterX)
	
	// create button
	val button = ControlButton(dialog.ref(), "Button")
	button.setClickedCallback(object: ControlButton.ClickedCallback() {
		override fun run(button: ControlButton) {
			Log.printf(Log.Level.Message, "%s Clicked\n", button.text)
		}
	})
	button.setAlign(Control.Align.Expand)
	button.setMargin(0.0f, 0.0f, 0.0f, 16.0f)
	
	// create sliders
	val color = Color.white()
	val slider_r = ControlSlider(dialog.ref(), "R", 2, color.r.toDouble(), 0.0, 1.0)
	val slider_g = ControlSlider(dialog.ref(), "G", 2, color.g.toDouble(), 0.0, 1.0)
	val slider_b = ControlSlider(dialog.ref(), "B", 2, color.b.toDouble(), 0.0, 1.0)
	slider_r.setChangedCallback(object: ControlSlider.ChangedCallback() { override fun run(slider: ControlSlider) { color.r = slider.valuef32 } })
	slider_g.setChangedCallback(object: ControlSlider.ChangedCallback() { override fun run(slider: ControlSlider) { color.g = slider.valuef32 } })
	slider_b.setChangedCallback(object: ControlSlider.ChangedCallback() { override fun run(slider: ControlSlider) { color.b = slider.valuef32 } })
	slider_r.setAlign(Control.Align.ExpandX)
	slider_g.setAlign(Control.Align.ExpandX)
	slider_b.setAlign(Control.Align.ExpandX)
	
	////////////////////////////////
	// scene test
	////////////////////////////////
	
	// main async
	var main_async = Async()
	if(!main_async.init()) return
	
	// process async
	var process_async = Async()
	if(!process_async.init()) return
	
	// manager cache
	SceneManager.setShaderCache("shader.cache")
	SceneManager.setTextureCache("texture.cache")
	
	// create scene manager
	var scene_manager = SceneManager()
	if(!scene_manager.create(device, SceneManager.Flags.DefaultFlags, object: SceneManager.CreateCallback() {
		override fun run(progress: Int) {
			Log.printf(Log.Level.Message, "SceneManager %d%%   \r", progress)
		}
	})) return
	Log.print("\n")
	
	// process thread
	var process_thread = object: Thread() {
		override fun run() {
			while(scene_manager.isValidPtr() && !scene_manager.isTerminated()) {
				if(!scene_manager.process(process_async)) Time.sleep(1000)
			}
			Log.print(Log.Level.Message, "Thread Done\n")
		}
	}
	process_thread.start()
	
	////////////////////////////////
	// render test
	////////////////////////////////
	
	// create render manager
	var render_manager = RenderManager(scene_manager)
	render_manager.setDrawParameters(device, window.colorFormat, window.depthFormat, window.multisample)
	if(!render_manager.create(device)) return
	if(!render_manager.create(device, RenderManager.Flags.DefaultFlags, object: RenderManager.CreateCallback() {
		override fun run(progress: Int) {
			Log.printf(Log.Level.Message, "RenderManager %d%%   \r", progress)
		}
	})) return
	Log.print("\n")
	
	// create render frame
	var render_frame = RenderFrame(render_manager)
	
	// render resources
	var render_renderer = render_manager.renderer
	var render_spatial = render_manager.spatial
	
	////////////////////////////////
	// scene test
	////////////////////////////////
	
	// create scene
	var scene = Scene(scene_manager, render_renderer.sceneRender)
	
	// create graph
	var graph = Graph(scene)
	
	// create camera
	var camera_position = Vector3d(12.0, 12.0, 6.0)
	var camera = CameraPerspective(scene)
	var node_camera = NodeCamera(graph, camera)
	node_camera.globalTransform = Matrix4x3d.placeTo(camera_position, Vector3d(0.0), Vector3d(0.0, 0.0, 1.0))
	render_frame.setCamera(node_camera)
	
	// create light
	var light = LightPoint(scene)
	var node_light = NodeLight(graph, light)
	node_light.globalTransform = Matrix4x3d.translate(camera_position)
	light.intensity = 100.0f
	light.radius = 1000.0f
	
	// create material
	var metallic_material = MaterialMetallic(scene)
	var material = Material(metallic_material.ref())
	material.setUniform("roughness_scale", 0.2f)
	material.setUniform("metallic_scale", 0.0f)
	
	// create object
	var object_mesh = ObjectMesh(scene)
	var node_object = NodeObject(graph, object_mesh)
	var mesh = create_mesh(Vector2i(64, 32), Vector2f(8.0f, 2.0f), 2.0f)
	object_mesh.create(mesh, material)
	mesh.destroyPtr()
	
	////////////////////////////////
	// main loop
	////////////////////////////////
	
	// main loop
	window.run(object: Window.MainLoopCallback() {
		
		var texture_frame = 0
		val texture_ifps = 1.0 / 30.0
		var texture_time = Time.seconds()
		
		override fun run(): Boolean {
			
			Window.update()
			
			// render window
			if(!window.render()) return false
			
			// update scene
			if(true) {
				
				// update render manager
				render_manager.update()
				
				// resize frame
				if(render_frame.width != window.width || render_frame.height != window.height) {
					if(!render_frame.create(device, render_renderer, window.width, window.height)) return false
					Log.printf(Log.Level.Message, "Frame Resized %dx%d\n", window.width, window.height)
				}
				
				// update diffuse texture
				if(Time.seconds() - texture_time > texture_ifps) {
					texture_time += texture_ifps
					var image = create_image(256, texture_frame)
					material.setTexture("diffuse", "procedural", image)
					material.updateScene()
					texture_frame++
				}
				
				// update graph
				val time = Time.seconds()
				node_object.globalTransform = Matrix4x3d.rotateZ(time * 24.0) * Matrix4x3d.rotateX(time * 16.0)
				node_object.updateScene()
				graph.updateSpatial()
				graph.updateScene()
				
				// update scene
				if(!scene.create(device, main_async)) return false
				scene.time = time
				scene.update(device)
				
				// update scene manager
				if(!scene_manager.update(device, main_async)) return false
			}
			
			// dispatch
			if(true) {
				
				var compute = device.createCompute()
				
				// dispatch scene
				scene_manager.dispatch(device, compute)
				scene.dispatch(device, compute)
				
				// dispatch render (multi-frame test)
				var render_frames = arrayOf(render_frame)
				render_spatial.dispatchFrames(compute, render_frames)
				render_spatial.dispatchObjects(compute, render_frames)
				render_renderer.dispatchFrames(compute, render_frames)
				
				compute.destroyPtr()
			}
			
			// draw
			if(true) {
				
				// flush buffers
				scene_manager.flush(device)
				render_manager.flush(device)
				render_frame.flush(device)
				
				// draw deferred
				render_renderer.drawDeferred(device, render_frame)
			}
			
			// dispatch
			if(true) {
				
				var compute = device.createCompute()
				
				// dispatch render
				render_renderer.dispatchLight(device, compute, render_frame)
				render_renderer.dispatchOccluder(device, compute, render_frame)
				render_renderer.dispatchLuminance(device, compute, render_frame)
				render_renderer.dispatchComposite(device, compute, render_frame)
				
				compute.destroyPtr()
			}
			
			// update interface
			if(true) {
				
				// window size
				val height = app.height.toFloat()
				val width = floor(height * window.width / window.height)
				val mouse_x = width * window.mouseX / window.width
				val mouse_y = height * window.mouseY / window.height
				
				// mouse button
				var buttons = 0
				if((window.mouseButtons.value and Window.Button.Left.value) != 0) buttons = buttons or Control.Button.Left.value
				if((window.mouseButtons.value and Window.Button.Left2.value) != 0) buttons = buttons or Control.Button.Left.value
				
				// render texture
				rect.setTexture(render_frame.compositeTexture, true)
				rect.setTextureScale(width / window.width, height / window.height)
				rect.setTextureFlip(false, render_renderer.isTargetFlipped())
				
				// update controls
				root.setViewport(width, height)
				root.setMouse(mouse_x, mouse_y, Control.Button(buttons))
				while(root.update(canvas.getScale(target))) { }
				
				// create canvas
				canvas.create(device, target)
			}
			
			// window target
			if(target.begin()) {
				
				val command = device.createCommand(target)
				
				// common parameters
				val time = Time.seconds().toFloat()
				val parameters = ByteBuffer.allocate(64 + 32).order(ByteOrder.LITTLE_ENDIAN)
				parameters.put(Matrix4x4f.rotateZ(time * 16.0f).bytes)
				parameters.put(color.bytes)
				parameters.putFloat(time)
				
				// draw background
				command.setPipeline(pipeline)
				command.setUniform(0, parameters)
				command.setVertices(0, vertices)
				command.setIndices(indices)
				command.drawElements(3)
				
				// draw canvas
				canvas.draw(command, target)
				
				// destroy pointer
				command.destroyPtr()
				
				target.end()
			}
			
			// present window
			if(!window.present()) return false
			
			// check device
			if(!device.check()) return false
			
			return true
		}
	})
	
	// stop process thread
	scene_manager.terminate()
	
	// finish context
	window.finish()
	
	// clear scene
	scene.clear()
	scene_manager.update(device, main_async)
	window.finish()
	
	// destroy resources
	scene.destroyPtr()
	render_frame.destroyPtr()
	render_manager.destroyPtr()
	scene_manager.destroyPtr()
	root.destroyPtr()
	
	// keep window alive
	window.unacquirePtr()
	
	// done
	Log.print(Log.Level.Message, "Done\n")
}
