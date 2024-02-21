#!/usr/bin/env python3

# MIT License
# 
# Copyright (C) 2018-2024, Tellusim Technologies Inc. https://tellusim.com/
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import sys
import math
import threading

from tellusimd import *

#
# create mesh
#
def create_mesh(steps, radius, texcoord):
	
	# create mesh
	mesh = Mesh()
	
	# create vertices
	num_vertices = (steps.x + 1) * (steps.y + 1)
	positions = MeshAttribute(MeshAttribute.TypePosition, FormatRGBf32, num_vertices)
	normals = MeshAttribute(MeshAttribute.TypeNormal, FormatRGBf32, num_vertices)
	tangents = MeshAttribute(MeshAttribute.TypeTangent, FormatRGBAf32, num_vertices)
	texcoords = MeshAttribute(MeshAttribute.TypeTexCoord, FormatRGf32, num_vertices)
	
	vertex = 0
	isteps = 1.0 / Vector2f(steps)
	aspect = radius.x / radius.y
	for j in range(steps.y + 1):
		ty = j * isteps.y
		z = -math.cos(ty * Pi2 - Pi05)
		r = math.sin(ty * Pi2 - Pi05)
		for i in range(steps.x + 1):
			tx = i * isteps.x
			x = -math.sin(tx * Pi2)
			y = math.cos(tx * Pi2)
			positions.set(vertex, Vector3f(x * (r * radius.y + radius.x), y * (r * radius.y + radius.x), z * radius.y))
			normals.set(vertex, Vector3f(x * r, y * r, z))
			tangents.set(vertex, Vector4f(-y, x, 0.0, 1.0))
			texcoords.set(vertex, Vector2f(tx * aspect, ty) * texcoord)
			vertex += 1
	
	basis = MeshAttribute(MeshAttribute.TypeBasis, FormatRGBAu32, num_vertices)
	basis.packAttributes(normals, tangents, FormatRGBAf16)
	
	# create indices
	num_indices = steps.x * steps.y * 4
	indices_format = FormatRu16 if num_vertices < Maxu16 else FormatRu32
	indices = MeshIndices(MeshIndices.TypeQuadrilateral, indices_format, num_indices)
	
	index = 0
	for j in range(steps.y):
		for i in range(steps.x):
			vertex = (steps.x + 1) * j + i
			indices.set(index, vertex, vertex + 1, vertex + steps.x + 2, vertex + steps.x + 1)
			index += 4
	
	# create geometry
	geometry = MeshGeometry(mesh)
	geometry.addAttribute(positions, indices)
	geometry.addAttribute(basis, indices)
	geometry.addAttribute(normals, indices)
	geometry.addAttribute(texcoords, indices)
	
	# geometry bounds
	hsize = Vector3f(Vector2f(radius.x + radius.y), radius.y)
	geometry.setBoundBox(BoundBoxf(-hsize, hsize))
	
	# create bounds
	mesh.createBounds()
	
	return mesh

#
# create image
#
def create_image(size, frame):
	
	# create image
	image = Image()
	image.create2D(FormatRGBAu8n, size)
	
	# create sampler
	sampler = ImageSampler(image)
	
	# fill image
	color = ImageColor(255)
	for y in range(size):
		for x in range(size):
			v = (((x - (frame ^ y)) ^ (y + (frame ^ x))) & 255) / 63.0
			color.r = int(math.cos(Pi * 1.0 + v) * 127.5 + 127.5)
			color.g = int(math.cos(Pi * 0.5 + v) * 127.5 + 127.5)
			color.b = int(math.cos(Pi * 0.0 + v) * 127.5 + 127.5)
			sampler.set2D(x, y, color)
	
	return image

#
# main
#
def main(argv):
	
	# create App
	app = App(argv)
	if not app.create(PlatformAny): return 1
	
	# create Window
	window = Window(app.getPlatform(), app.getDevice())
	if not window: return 1
	
	window.setSize(app.getWidth(), app.getHeight())
	window.setCloseClickedCallback(lambda: window.stop())
	
	def clicked_callback(key, code):
		if key == Window.KeyEsc: window.stop()
		if key == Window.KeyF12:
			image = Image()
			if window.grab(image) and image.save('screenshot.png'):
				Log.print(Log.Message, 'Screenshot\n')
	
	window.setKeyboardPressedCallback(clicked_callback)
	
	title = window.getPlatformName() + ' Tellusim::Python'
	if not window.create(title, Window.DefaultFlags | Window.FlagVerticalSync) or not window.setHidden(False): return 1
	
	# create device
	device = Device(window)
	if not device: return 1
	
	# device features
	Log.printf(Log.Message, 'Features:\n%s\n', device.getFeatures())
	Log.printf(Log.Message, 'Device: %s\n', device.getName())
	
	# create target
	target = device.createTarget(window)
	if not target: return 1
	
	# build info
	Log.printf(Log.Message, 'Build: %s\n', App.getBuildInfo())
	
	################################
	# core test
	################################
	
	blob = Blob()
	blob.writeString(title)
	
	blob.seek(0)
	Log.printf(Log.Message, 'Stream: %s\n', blob.readString())
	
	################################
	# platform test
	################################
	
	# create pipeline
	pipeline = device.createPipeline()
	pipeline.setUniformMask(0, Shader.MaskFragment)
	pipeline.setColorFormat(window.getColorFormat())
	pipeline.setDepthFormat(window.getDepthFormat())
	pipeline.addAttribute(Pipeline.AttributePosition, FormatRGf32, 0, 0, 8)
	if not pipeline.loadShaderGLSL(Shader.TypeVertex, 'main.shader', 'VERTEX_SHADER=1'): return 1
	if not pipeline.loadShaderGLSL(Shader.TypeFragment, 'main.shader', 'FRAGMENT_SHADER=1'): return 1
	if not pipeline.create(): return 1
	
	# geometry vertices
	vertices = bytearray(Vector2f(3.0, -1.0)) + Vector2f(-1.0, -1.0) + Vector2f(-1.0, 3.0)
	
	# geometry indices
	indices = bytearray(Vector3u(0, 1, 2))
	
	################################
	# interface test
	################################
	
	# create canvas
	canvas = Canvas()
	
	# create root controls
	root = ControlRoot(canvas, True)
	root.setFontSize(24)
	
	# create rect
	rect = ControlRect(root, CanvasElement.ModeTexture)
	rect.setAlign(Control.AlignExpand)
	rect.setFullscreen(True)
	
	# create dialog
	dialog = ControlDialog(root, 1, 8.0, 8.0)
	def updated_callback(dialog):
		x = int(dialog.getPositionX())
		y = int(dialog.getPositionY())
		width = int(dialog.getWidth())
		height = int(dialog.getHeight())
		Log.printf(Log.Message, 'Dialog Updated %d %d %ux%u\n', x, y, width, height)
	dialog.setUpdatedCallback(updated_callback)
	dialog.setAlign(Control.AlignCenter)
	dialog.setSize(240.0, 180.0)
	
	# create text
	text = ControlText(dialog, title)
	text.setAlign(Control.AlignCenterX)
	
	# create button
	button = ControlButton(dialog, 'Button')
	def clicked_callback(button): Log.printf(Log.Message, '%s Clicked\n', button.getText())
	button.setClickedCallback(clicked_callback)
	button.setAlign(Control.AlignExpand)
	button.setMargin(0.0, 0.0, 0.0, 16.0)
	
	# common parameters
	color = Color.white
	
	# create sliders
	slider_r = ControlSlider(dialog, 'R', 2, color.r, 0.0, 1.0)
	slider_g = ControlSlider(dialog, 'G', 2, color.g, 0.0, 1.0)
	slider_b = ControlSlider(dialog, 'B', 2, color.b, 0.0, 1.0)
	def changed_r_callback(slider): color.r = slider.getValuef32()
	def changed_g_callback(slider): color.g = slider.getValuef32()
	def changed_b_callback(slider): color.b = slider.getValuef32()
	slider_r.setChangedCallback(changed_r_callback)
	slider_g.setChangedCallback(changed_g_callback)
	slider_b.setChangedCallback(changed_b_callback)
	slider_r.setAlign(Control.AlignExpandX)
	slider_g.setAlign(Control.AlignExpandX)
	slider_b.setAlign(Control.AlignExpandX)
	
	################################
	# scene test
	################################
	
	# main async
	main_async = Async()
	if not main_async.init(): return 1
	
	# process async
	process_async = Async()
	if not process_async.init(): return 1
	
	# manager cache
	SceneManager.setShaderCache('shader.cache')
	SceneManager.setTextureCache('texture.cache')
	
	# create scene manager
	scene_manager = SceneManager()
	if True:
		if not scene_manager.create(device, SceneManager.DefaultFlags, lambda progress: Log.printf(Log.Message, 'SceneManager %u%%   \r', progress)): return 1
		Log.print('\n')
	else:
		if not scene_manager.create(device): return 1
	
	# process thread
	def process_callback():
		while scene_manager and not scene_manager.isTerminated():
			if not scene_manager.process(process_async): Time.sleep(1000)
		Log.print(Log.Message, 'Thread Done\n')
	
	thread = threading.Thread(None, process_callback)
	thread.start()
	
	################################
	# render test
	################################
	
	# create render manager
	render_manager = RenderManager(scene_manager)
	render_manager.setDrawParameters(device, window.getColorFormat(), window.getDepthFormat(), window.getMultisample())
	if True:
		if not render_manager.create(device, RenderManager.DefaultFlags, lambda progress: Log.printf(Log.Message, 'RenderManager %u%%   \r', progress)): return 1
		Log.print('\n')
	else:
		if not render_manager.create(device): return 1
	
	# create render frame
	render_frame = RenderFrame(render_manager)
	
	# render resources
	render_renderer = render_manager.getRenderer()
	render_spatial = render_manager.getSpatial()
	
	################################
	# scene test
	################################
	
	# create scene
	scene = Scene(scene_manager, render_renderer.getSceneRender())
	
	# create graph
	graph = Graph(scene)
	
	# create camera
	camera_position = Vector3d(12.0, 12.0, 6.0)
	camera = CameraPerspective(scene)
	node_camera = NodeCamera(graph, camera)
	node_camera.setGlobalTransform(Matrix4x3d.placeTo(camera_position, Vector3d(0.0), Vector3d(0.0, 0.0, 1.0)))
	
	# create light
	light = LightPoint(scene)
	node_light = NodeLight(graph, light)
	node_light.setGlobalTransform(Matrix4x3d.translate(camera_position))
	light.setIntensity(100.0)
	light.setRadius(1000.0)
	
	# create material
	metallic_material = MaterialMetallic(scene)
	material = Material(metallic_material)
	material.setUniform(MaterialMetallic.UniformRoughnessScale, 0.2)
	material.setUniform(MaterialMetallic.UniformMetallicScale, 0.0)
	
	# create object
	object_mesh = ObjectMesh(scene)
	node_object = NodeObject(graph, object_mesh)
	mesh = create_mesh(Vector2u(64, 32), Vector2f(8.0, 2.0), 2.0)
	object_mesh.create(mesh, material)
	
	################################
	# main loop
	################################
	
	texture_frame = 0
	texture_ifps = 1.0 / 3.0
	texture_time = 0.0
	
	# main loop
	def main_loop():
		nonlocal texture_frame
		nonlocal texture_ifps
		nonlocal texture_time
		
		# update window
		Window.update()
		
		# render window
		if not window.render(): return False
		
		# update scene
		if True:
			
			# update render manager
			render_manager.update()
			
			# resize frame
			if render_frame.getWidth() != window.getWidth() or render_frame.getHeight() != window.getHeight():
				if not render_frame.create(device, render_renderer, window.getWidth(), window.getHeight()): return False
				Log.printf(Log.Message, 'Frame Resized %ux%u\n', window.getWidth(), window.getHeight())
			
			# update diffuse texture
			if Time.seconds() - texture_time > texture_ifps:
				texture_time += texture_ifps
				image = create_image(128, texture_frame)
				material.setTexture(MaterialMetallic.TextureDiffuse, 'procedural', image)
				material.updateScene()
				texture_frame += 1
			
			# update graph
			time = Time.seconds()
			node_object.setGlobalTransform(Matrix4x3d.rotateZ(time * 24.0) * Matrix4x3d.rotateX(time * 16.0))
			node_object.updateScene()
			graph.updateSpatial()
			graph.updateScene()
			
			# update scene
			if not scene.create(device, main_async): return False
			scene.setTime(time)
			scene.update()
			
			# update scene manager
			if not scene_manager.update(device, main_async): return False
		
		# dispatch
		if True:
			
			compute = device.createCompute()
			
			# dispatch scene
			scene_manager.dispatch(device, compute)
			scene.dispatch(device, compute, node_camera)
			
			# dispatch render (multi-frame test)
			render_frames = [ render_frame ]
			render_spatial.dispatchFrames(compute, node_camera, render_frames)
			render_spatial.dispatchObjects(compute, render_frames)
			render_renderer.dispatchFrames(compute, render_frames)
			
			compute = None
		
		# draw
		if True:
			
			# flush buffers
			scene_manager.flush(device)
			render_manager.flush(device)
			render_frame.flush(device)
			
			# draw deferred
			render_renderer.drawDeferred(device, render_frame)
		
		# dispatch
		if True:
			
			compute = device.createCompute()
			
			# dispatch render
			render_renderer.dispatchLight(device, compute, render_frame)
			render_renderer.dispatchOccluder(device, compute, render_frame)
			render_renderer.dispatchLuminance(device, compute, render_frame)
			render_renderer.dispatchComposite(device, compute, render_frame)
			
			compute = None
		
		# update interface
		if True:
			
			# window size
			height = app.getHeight()
			width = math.floor(height * window.getWidth() / window.getHeight())
			mouse_x = width * window.getMouseX() / window.getWidth()
			mouse_y = height * window.getMouseY() / window.getHeight()
			
			# mouse button
			buttons = Control.ButtonNone
			if window.getMouseButtons() & Window.ButtonLeft: buttons |= Control.ButtonLeft
			if window.getMouseButtons() & Window.ButtonLeft2: buttons |= Control.ButtonLeft
			
			# render texture
			rect.setTexture(render_frame.getCompositeTexture())
			rect.setTextureScale(width / window.getWidth(), height / window.getHeight())
			rect.setTextureFlip(False, render_renderer.isTargetFlipped())
			
			# update controls
			root.setViewport(width, height)
			root.setMouse(mouse_x, mouse_y, buttons)
			while root.update(canvas.getScale(target)): pass
			
			# create canvas
			canvas.create(device, target)
		
		# window target
		if target.begin():
			
			command = device.createCommand(target)
			
			# current time
			time = Time.seconds()
			
			# common parameters
			parameters = bytearray()
			parameters += Matrix4x4f.rotateZ(time * 16.0)
			parameters += color
			parameters += Scalarf(time)
			
			# draw background
			command.setPipeline(pipeline)
			command.setUniform(0, parameters)
			command.setVertices(0, vertices)
			command.setIndices(FormatRu32, indices)
			command.drawElements(3)
			
			# draw canvas
			canvas.draw(command, target)
			
			command = None
			
			target.end()
		
		# present window
		if not window.present(): return False
		
		# check device
		if not device.check(): return False;
		
		return True
	
	window.run(main_loop)
	
	# stop process thread
	scene_manager.terminate()
	
	# finish context
	window.finish()
	
	# clear scene
	scene.clear()
	scene_manager.update(device, main_async)
	window.finish()
	
	# wait thread
	thread.join()
	
	# done
	Log.print('Done\n')
	
	return 0

#
# entry point
#
if __name__ == '__main__':
	try:
		exit(main(sys.argv))
	except Exception as error:
		print('\n' + str(error))
		exit(1)
