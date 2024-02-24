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

import Tellusim

/*
 */
func create_mesh(_ steps: Vector2u,_ radius: Vector2f,_ texcoord: Float32) -> Mesh {
	
	// create mesh
	let mesh = Mesh()
	
	// create vertices
	let num_vertices = (steps.x + 1) * (steps.y + 1)
	let positions = MeshAttribute(MeshAttribute.Kind.Position, Format.RGBf32, num_vertices)
	let normals = MeshAttribute(MeshAttribute.Kind.Normal, Format.RGBf32, num_vertices)
	let tangents = MeshAttribute(MeshAttribute.Kind.Tangent, Format.RGBAf32, num_vertices)
	let texcoords = MeshAttribute(MeshAttribute.Kind.TexCoord, Format.RGf32, num_vertices)
	
	var vertex = UInt32(0)
	let isteps = Vector2f(1.0) / Vector2f(steps)
	let aspect = radius.x / radius.y
	for j in 0...steps.y {
		let ty = Float32(j) * isteps.y
		let z = -cos(ty * Pi2 - Pi05)
		let r = sin(ty * Pi2 - Pi05)
		for i in 0...steps.x {
			let tx = Float32(i) * isteps.x
			let x = -sin(tx * Pi2)
			let y = cos(tx * Pi2)
			positions.set(vertex, Vector3f(x * (r * radius.y + radius.x), y * (r * radius.y + radius.x), z * radius.y))
			normals.set(vertex, Vector3f(x * r, y * r, z))
			tangents.set(vertex, Vector4f(-y, x, 0.0, 1.0))
			texcoords.set(vertex, Vector2f(tx * aspect, ty) * texcoord)
			vertex += 1
		}
	}
	
	let basis = MeshAttribute(MeshAttribute.Kind.Basis, Format.RGBAu32, num_vertices)
	basis.packAttributes(normals, tangents, Format.RGBAf16)
	
	// create indices
	let num_indices = steps.x * steps.y * 4
	let indices_format = (num_vertices < Maxu16) ? Format.Ru16 : Format.Ru32
	let indices = MeshIndices(MeshIndices.Kind.Quadrilateral, indices_format, num_indices)
	
	var index = UInt32(0)
	for j in 0..<steps.y {
		for i in 0..<steps.x {
			vertex = (steps.x + 1) * j + i
			indices.set(index, vertex, vertex + 1, vertex + steps.x + 2, vertex + steps.x + 1)
			index += 4
		}
	}
	
	// create geometry
	let geometry = MeshGeometry(mesh)
	geometry.addAttribute(positions, indices)
	geometry.addAttribute(basis, indices)
	geometry.addAttribute(normals, indices)
	geometry.addAttribute(texcoords, indices)
	
	// geometry bounds
	let hsize = Vector3f(Vector2f(radius.x + radius.y), radius.y)
	geometry.setBoundBox(BoundBoxf(min: -hsize, max: hsize))
	
	// create bounds
	mesh.createBounds()
	
	return mesh
}

/*
 */
func create_image(_ size: UInt32,_ frame: UInt32) -> Image {
	
	// create image
	let image = Image()
	image.create2D(Format.RGBAu8n, size, Image.Flags.None)
	
	// create sampler
	let sampler = ImageSampler(image)
	
	// fill image
	var color = ImageColor(UInt32(255))
	for y in 0..<size {
		for x in 0..<size {
			let v = Float32(((Int32(x) - Int32(frame ^ y)) ^ (Int32(y) + Int32(frame ^ x))) & 255) / 63.0
			color.u.r = UInt32(cos(Pi * 1.0 + v) * 127.5 + 127.5)
			color.u.g = UInt32(cos(Pi * 0.5 + v) * 127.5 + 127.5)
			color.u.b = UInt32(cos(Pi * 0.0 + v) * 127.5 + 127.5)
			sampler.set2D(x, y, color)
		}
	}
	
	return image
}

/*
 */
func main() -> Int32 {
	
	// create app
	let app = App()
	if !app.create(Platform.Any, UInt32(App.Values.Version.rawValue)) { return 1 }
	
	// create window
	let window = Window(app.getPlatform(), app.getDevice())
	if !window { return 1 }
	
	window.setSize(app.getWidth(), app.getHeight())
	window.setCloseClickedCallback(Window.CloseClickedFunction({() in window.stop() }))
	window.setKeyboardPressedCallback(Window.KeyboardPressedFunction({ (key: UInt32, code: UInt32) in
		if key == Window.Key.Esc.rawValue { window.stop() }
		if key == Window.Key.F12.rawValue {
			let image = Image()
			if window.grab(image) && image.save("screenshot.png") {
				print("Screenshot")
			}
		}
	}))
	
	let title = window.getPlatformName() + " Tellusim::Swift"
	if !window.create(title) || !window.setHidden(false) { return 1 }
	
	// create device
	let device = Device(window)
	if !device { return 1 }
	
	// device features
	print("Features: ", device.getFeatures())
	print("Device: ", device.getName())
	
	// create target
	let target = device.createTarget(window)
	if !target{ return 1 }
	
	// build info
	print("Build: ", App.getBuildInfo())
	
	////////////////////////////////
	// core test
	////////////////////////////////
	
	let blob = Blob()
	blob.writeString(title)
	
	blob.seek(0)
	print("Stream: ", blob.readString())
	
	////////////////////////////////
	// platform test
	////////////////////////////////
	
	// create pipeline
	let pipeline = device.createPipeline()
	pipeline.setUniformMask(0, Shader.Mask.Fragment)
	pipeline.setColorFormat(window.getColorFormat())
	pipeline.setDepthFormat(window.getDepthFormat())
	pipeline.addAttribute(Pipeline.Attribute.Position, Format.RGf32, 0, 0, 8)
	if !pipeline.loadShaderGLSL(Shader.Kind.Vertex, "main.shader", "VERTEX_SHADER=1") { return 1 }
	if !pipeline.loadShaderGLSL(Shader.Kind.Fragment, "main.shader", "FRAGMENT_SHADER=1") { return 1 }
	if !pipeline.create() { return 1 }
	
	// geometry vertices
	var vertices: [Vector2f] = [ Vector2f(3.0, -1.0), Vector2f(-1.0, -1.0), Vector2f(-1.0, 3.0) ]
	
	// geometry indices
	var indices: [UInt16] = [ 0, 1, 2 ]
	
	////////////////////////////////
	// interface test
	////////////////////////////////
	
	// create canvas
	let canvas = Canvas()
	
	// create root controls
	let root = ControlRoot(canvas, true)
	root.setFontSize(24)
	
	// create rect
	let rect = ControlRect(root, CanvasElement.Mode.Texture)
	rect.setAlign(Control.Align.Expand)
	rect.setFullscreen(true)
	
	// create dialog
	let dialog = ControlDialog(root, 1, 8.0, 8.0)
	dialog.setUpdatedCallback(ControlDialog.UpdatedFunction({ (dialog: ControlDialog) in
		let x = dialog.getPositionX()
		let y = dialog.getPositionY()
		let width = dialog.getWidth()
		let height = dialog.getHeight()
		print("Dialog Updated \(x) \(y) \(width)x\(height)")
	}))
	dialog.setAlign(Control.Align.Center)
	dialog.setSize(240.0, 180.0)
	
	// create text
	let text = ControlText(dialog, title)
	text.setAlign(Control.Align.CenterX)
	
	// create button
	let button = ControlButton(dialog, "Button")
	button.setClickedCallback(ControlButton.ClickedFunction({ (button: ControlButton) in
		print(button.getText(), "Clicked")
	}))
	button.setAlign(Control.Align.Expand)
	button.setMargin(0.0, 0.0, 0.0, 16.0)
	
	// common parameters
	struct CommonParameters {
		var transform: Matrix4x4f
		var color: Color
		var time: Float32
	}
	var parameters = CommonParameters(
		transform: Matrix4x4f.identity,
		color: Color(1.0),
		time: 0.0
	)
	
	// create sliders
	let slider_r = ControlSlider(dialog, "R", 2, Float64(parameters.color.r), 0.0, 1.0)
	let slider_g = ControlSlider(dialog, "G", 2, Float64(parameters.color.g), 0.0, 1.0)
	let slider_b = ControlSlider(dialog, "B", 2, Float64(parameters.color.b), 0.0, 1.0)
	slider_r.setChangedCallback(ControlSlider.ChangedFunction({ (slider: ControlSlider) in parameters.color.r = slider.getValuef32() }))
	slider_g.setChangedCallback(ControlSlider.ChangedFunction({ (slider: ControlSlider) in parameters.color.g = slider.getValuef32() }))
	slider_b.setChangedCallback(ControlSlider.ChangedFunction({ (slider: ControlSlider) in parameters.color.b = slider.getValuef32() }))
	slider_r.setAlign(Control.Align.ExpandX)
	slider_g.setAlign(Control.Align.ExpandX)
	slider_b.setAlign(Control.Align.ExpandX)
	
	////////////////////////////////
	// scene test
	////////////////////////////////
	
	// main async
	var main_async = Async()
	if !main_async.initialize() { return 1 }
	
	// process async
	let process_async = Async()
	if !process_async.initialize() { return 1 }
	
	// manager cache
	SceneManager.setShaderCache("shader.cache")
	SceneManager.setTextureCache("texture.cache")
	
	// create scene manager
	let scene_manager = SceneManager()
	if !scene_manager.create(device) { return 1 }
	
	// process thread
	class ProcessThread : Thread {
		init(_ manager: SceneManager, _ async : Async) {
			self.manager = manager
			self.async = async
			super.init()
		}
		override func main() {
			while manager.isValidPtr() && !manager.isTerminated() {
				if !manager.process(async) { Time.sleep(1000) }
			}	
			print("Thread Done")
		}
		var manager: SceneManager
		var async: Async
	}
	let thread = ProcessThread(scene_manager, process_async)
	thread.start()
	
	////////////////////////////////
	// render test
	////////////////////////////////
	
	// create render manager
	let render_manager = RenderManager(scene_manager)
	render_manager.setDrawParameters(device, window.getColorFormat(), window.getDepthFormat(), window.getMultisample())
	if !render_manager.create(device) { return 1 }
	
	// create render frame
	let render_frame = RenderFrame(render_manager)
	
	// render resources
	let render_renderer = render_manager.getRenderer()
	let render_spatial = render_manager.getSpatial()
	
	////////////////////////////////
	// scene test
	////////////////////////////////
	
	// create scene
	let scene = Scene(scene_manager, render_renderer.getSceneRender())
	
	// create graph
	let graph = Graph(scene)
	
	// create camera
	let camera_position = Vector3d(12.0, 12.0, 6.0)
	let camera = CameraPerspective(scene)
	let node_camera = NodeCamera(graph, camera)
	node_camera.setGlobalTransform(Matrix4x3d.placeTo(camera_position, Vector3d(0.0), Vector3d(0.0, 0.0, 1.0)))
	render_frame.setCamera(node_camera)
	
	// create light
	let light = LightPoint(scene)
	let node_light = NodeLight(graph, light)
	node_light.setGlobalTransform(Matrix4x3d.translate(camera_position))
	light.setIntensity(100.0)
	light.setRadius(1000.0)
	
	// create material
	let metallic_material = MaterialMetallic(scene)
	var material = Material(metallic_material)
	material.setUniform("roughness_scale", 0.2)
	material.setUniform("metallic_scale", 0.0)
	
	// create object
	let object_mesh = ObjectMesh(scene)
	let node_object = NodeObject(graph, object_mesh)
	let mesh = create_mesh(Vector2u(64, 32), Vector2f(8.0, 2.0), 2.0)
	object_mesh.create(mesh, &material)
	mesh.destroyPtr()
	
	////////////////////////////////
	// main loop
	////////////////////////////////
	
	var texture_frame = UInt32(0)
	let texture_ifps = 1.0 / 30.0
	var texture_time = Time.seconds()
	
	// main loop
	window.run(Window.MainLoopFunction({ () -> Bool in
		
		// update window
		Window.update()
		
		// render window
		if !window.render() { return false }
		
		// update scene
		if true {
			
			// update render manager
			render_manager.update()
			
			// resize frame
			if render_frame.getWidth() != window.getWidth() || render_frame.getHeight() != window.getHeight() {
				if !render_frame.create(device, render_renderer, window.getWidth(), window.getHeight()) { return false }
				print("Frame Resized \(window.getWidth())x\(window.getHeight())")
			}
			
			// update diffuse texture
			if Time.seconds() - texture_time > texture_ifps {
				texture_time += texture_ifps
				let image = create_image(256, texture_frame)
				material.setTexture("diffuse", "procedural", image)
				material.updateScene()
				texture_frame += 1
			}
			
			// update graph
			let time = Time.seconds()
			node_object.setGlobalTransform(Matrix4x3d.rotateZ(time * 24.0) * Matrix4x3d.rotateX(time * 16.0))
			node_object.updateScene()
			graph.updateSpatial()
			graph.updateScene()
			
			// update scene
			if !scene.create(device, &main_async) { return false }
			scene.setTime(time)
			scene.update(device)
			
			// update scene manager
			if !scene_manager.update(device, main_async) { return false }
		}
		
		// dispatch
		if true {
			
			let compute = device.createCompute()
			
			// dispatch scene
			scene_manager.dispatch(device, compute)
			scene.dispatch(device, compute)
			
			// dispatch render (multi-frame test)
			render_spatial.dispatchFrame(compute, render_frame)
			render_spatial.dispatchObjects(compute, render_frame)
			render_renderer.dispatchFrame(compute, render_frame)
		}
		
		// draw
		if true {
			
			// flush buffers
			scene_manager.flush(device)
			render_manager.flush(device)
			render_frame.flush(device)
			
			// draw deferred
			render_renderer.drawDeferred(device, render_frame)
		}
		
		// dispatch
		if true {
			
			let compute = device.createCompute()
			
			// dispatch render
			render_renderer.dispatchLight(device, compute, render_frame)
			render_renderer.dispatchOccluder(device, compute, render_frame)
			render_renderer.dispatchLuminance(device, compute, render_frame)
			render_renderer.dispatchComposite(device, compute, render_frame)
		}
		
		// update interface
		if true {
			
	 		// window size
	 		let height = Float32(app.getHeight())
	 		let width = floor(height * Float32(window.getWidth()) / Float32(window.getHeight()))
	 		let mouse_x = width * Float32(window.getMouseX()) / Float32(window.getWidth())
	 		let mouse_y = height * Float32(window.getMouseY()) / Float32(window.getHeight())
	 		
	 		// mouse button
	 		var buttons = Control.Button.None
	 		if (window.getMouseButtons() & Window.Button.Left) != Window.Button.None { buttons |= Control.Button.Left }
	 		if (window.getMouseButtons() & Window.Button.Left2) != Window.Button.None { buttons |= Control.Button.Left }
	 		
	 		// render texture
			rect.setTexture(render_frame.getCompositeTexture(), true)
			rect.setTextureScale(width / Float32(window.getWidth()), height / Float32(window.getHeight()))
			rect.setTextureFlip(false, render_renderer.isTargetFlipped())
	 		
	 		// update controls
	 		root.setViewport(width, height)
	 		root.setMouse(mouse_x, mouse_y, buttons)
	 		while(root.update(canvas.getScale(target))) { }
	 		
	 		// create canvas
	 		canvas.create(device, target)
		}
		
		// window target
		if target.begin() {
			
			let command = device.createCommand(target)
			
			// current time
			let time = Float32(Time.seconds())
			
			// common parameters
			parameters.transform = Matrix4x4f.rotateZ(time * 16.0)
			parameters.time = time
			
			// draw background
			command.setPipeline(pipeline)
			command.setUniform(0, &parameters)
			command.setVertices(0, &vertices)
			command.setIndices(&indices)
			command.drawElements(3)
			
			// draw canvas
			canvas.draw(command, target)
		}
		target.end()
		
		// present window
		if !window.present() { return false }
		
		// check device
		if !device.check() { return false }
		
		return true
	}))
	
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
	
	// done
	print("Done")
	
	return 0
}

/*
 */
print(main())
