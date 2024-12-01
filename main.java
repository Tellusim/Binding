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

package com.main;

import com.tellusim.*;

import java.lang.Math;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

/*
 */
public class main {
	
	/*
	 */
	static {
		Base.loadDebug();
	}
	
	/*
	 */
	static Mesh create_mesh(Vector2i steps, Vector2f radius, float texcoord) {
		
		// create mesh
		Mesh mesh = new Mesh();
		
		// create vertices
		int num_vertices = (steps.x() + 1) * (steps.y() + 1);
		MeshAttribute positions = new MeshAttribute(MeshAttribute.Type.Position, Format.RGBf32, num_vertices);
		MeshAttribute normals = new MeshAttribute(MeshAttribute.Type.Normal, Format.RGBf32, num_vertices);
		MeshAttribute tangents = new MeshAttribute(MeshAttribute.Type.Tangent, Format.RGBAf32, num_vertices);
		MeshAttribute texcoords = new MeshAttribute(MeshAttribute.Type.TexCoord, Format.RGf32, num_vertices);
		
		int vertex = 0;
		Vector2f isteps = (new Vector2f(1.0f)).div(new Vector2f(steps));
		float aspect = radius.x() / radius.y();
		for(int j = 0; j <= steps.y(); j++) {
			float ty = j * isteps.y();
			float z = -(float)Math.cos(ty * Base.Pi2 - Base.Pi05);
			float r = (float)Math.sin(ty * Base.Pi2 - Base.Pi05);
			for(int i = 0; i <= steps.x(); i++) {
				float tx = i * isteps.x();
				float x = -(float)Math.sin(tx * Base.Pi2);
				float y = (float)Math.cos(tx * Base.Pi2);
				positions.set(vertex, new Vector3f(x * (r * radius.y() + radius.x()), y * (r * radius.y() + radius.x()), z * radius.y()));
				normals.set(vertex, new Vector3f(x * r, y * r, z));
				tangents.set(vertex, new Vector4f(-y, x, 0.0f, 1.0f));
				texcoords.set(vertex, (new Vector2f(tx * aspect, ty)).mul(texcoord));
				vertex++;
			}
		}
		
		MeshAttribute basis = new MeshAttribute(MeshAttribute.Type.Basis, Format.RGBAu32, num_vertices);
		basis.packAttributes(normals, tangents, Format.RGBAf16);
		
		// create indices
		int num_indices = steps.x() * steps.y() * 4;
		Format.Enum indices_format = (num_vertices < Base.Maxu16) ? Format.Ru16 : Format.Ru32;
		MeshIndices indices = new MeshIndices(MeshIndices.Type.Quadrilateral, indices_format, num_indices);
		
		int index = 0;
		for(int j = 0; j < steps.y(); j++) {
			for(int i = 0; i < steps.x(); i++) {
				vertex = (steps.x() + 1) * j + i;
				indices.set(index, vertex, vertex + 1, vertex + steps.x() + 2, vertex + steps.x() + 1);
				index += 4;
			}
		}
		
		// create geometry
		MeshGeometry geometry = new MeshGeometry(mesh);
		geometry.addAttribute(positions, indices);
		geometry.addAttribute(basis, indices);
		geometry.addAttribute(normals, indices);
		geometry.addAttribute(texcoords, indices);
		
		// geometry bounds
		Vector3f hsize = new Vector3f(new Vector2f(radius.x() + radius.y()), radius.y());
		geometry.setBoundBox(new BoundBoxf(hsize.neg(), hsize));
		
		// create bounds
		mesh.createBounds();
		
		return mesh;
	}
	
	/*
	 */
	static Image create_image(int size, int frame) {
		
		// create image
		Image image = new Image();
		image.create2D(Format.RGBAu8n, size);
		
		// create sampler
		ImageSampler sampler = new ImageSampler(image, new Slice());
		
		// fill image
		ImageColor color = new ImageColor(255);
		for(int y = 0; y < size; y++) {
			for(int x = 0; x < size; x++) {
				float v = (float)(((x - (frame ^ y)) ^ (y + (frame ^ x))) & 255) / 63.0f;
				color.setR((int)(Math.cos(Base.Pi * 1.0f + v) * 127.5f + 127.5f));
				color.setG((int)(Math.cos(Base.Pi * 0.5f + v) * 127.5f + 127.5f));
				color.setB((int)(Math.cos(Base.Pi * 0.0f + v) * 127.5f + 127.5f));
				sampler.set2D(x, y, color);
			}
		}
		
		return image;
	}
	
	/*
	 */
	public static void main(String[] args) {
		
		// create app
		App app = new App(args);
		if(!app.create(Platform.Any)) return;
		
		// create window
		Window window = new Window(app.getPlatform(), app.getDevice());
		if(!window.isValidPtr()) return;
		
		window.setSize(app.getWidth(), app.getHeight());
		window.setCloseClickedCallback(new Window.CloseClickedCallback() {
			public void run() { window.stop(); }
		});
		window.setKeyboardPressedCallback(new Window.KeyboardPressedCallback() {
			public void run(int key, int code) {
				if(key == Window.Key.Esc.value) window.stop();
				if(key == Window.Key.F12.value) {
					Image image = new Image();
					if(window.grab(image) && image.save("screenshot.png")) {
						Log.print(Log.Level.Message, "Screenshot\n");
					}
				}
			}
		});
		
		String title = window.getPlatformName() + " Tellusim::Java";
		if(!window.create(title, Window.Flags.DefaultFlags.or(Window.Flags.VerticalSync)) || !window.setHidden(false)) return;
		
		// create device
		Device device = new Device(window);
		if(!device.isValidPtr()) return;
		
		// device features
		Log.printf(Log.Level.Message, "Features:\n%s\n", device.getFeatures());
		Log.printf(Log.Level.Message, "Device: %s\n", device.getName());
		
		// build info
		Log.printf(Log.Level.Message, "Build: %s\n", app.getBuildInfo());
		
		// create target
		Target target = device.createTarget(window);
		if(!target.isValidPtr()) return;
		
		////////////////////////////////
		// core test
		////////////////////////////////
		
		Blob blob = new Blob();
		blob.writeString(title);
		
		blob.seek(0);
		Log.printf(Log.Level.Message, "Stream: %s\n", blob.readString());
		
		////////////////////////////////
		// bounds test
		////////////////////////////////
		
		// 32-bit floating point
		BoundBoxf bound_boxf = new BoundBoxf(Vector3f.one().neg(), Vector3f.one());
		BoundSpheref bound_spheref = new BoundSpheref(bound_boxf);
		BoundFrustumf bound_frustumf = new BoundFrustumf(Matrix4x4f.perspective(60.0f, 1.0f, 0.1f, 1000.0f), Matrix4x4f.lookAt(Vector3f.one(), Vector3f.zero(), new Vector3f(0.0f, 0.0f, 1.0f)));
		Log.printf(Log.Level.Message, "%s %b %s %b\n", bound_boxf.toString(), bound_frustumf.inside(bound_boxf), bound_spheref.toString(), bound_frustumf.inside(bound_spheref));
		bound_boxf = Matrix4x3f.translate(10.0f, 0.0f, 0.0f).mul(bound_boxf);
		bound_spheref = Matrix4x4f.translate(10.0f, 0.0f, 0.0f).mul(bound_spheref);
		Log.printf(Log.Level.Message, "%s %b %s %b\n", bound_boxf.toString(), bound_frustumf.inside(bound_boxf), bound_spheref.toString(), bound_frustumf.inside(bound_spheref));
		
		// 64-bit floating point
		BoundBoxd bound_boxd = new BoundBoxd(Vector3d.one().neg(), Vector3d.one());
		BoundSphered bound_sphered = new BoundSphered(bound_boxd);
		BoundFrustumd bound_frustumd = new BoundFrustumd(Matrix4x4d.perspective(60.0, 1.0, 0.1, 1000.0), Matrix4x4d.lookAt(Vector3d.one(), Vector3d.zero(), new Vector3d(0.0, 0.0, 1.0)));
		Log.printf(Log.Level.Message, "%s %b %s %b\n", bound_boxd.toString(), bound_frustumd.inside(bound_boxd), bound_sphered.toString(), bound_frustumd.inside(bound_sphered));
		bound_boxd = Matrix4x3d.translate(10.0, 0.0, 0.0).mul(bound_boxd);
		bound_sphered = Matrix4x4d.translate(10.0, 0.0, 0.0).mul(bound_sphered);
		Log.printf(Log.Level.Message, "%s %b %s %b\n", bound_boxd.toString(), bound_frustumd.inside(bound_boxd), bound_sphered.toString(), bound_frustumd.inside(bound_sphered));
		
		////////////////////////////////
		// platform test
		////////////////////////////////
		
		// create pipeline
		Pipeline pipeline = device.createPipeline();
		pipeline.setUniformMask(0, Shader.Mask.Fragment);
		pipeline.setColorFormat(window.getColorFormat());
		pipeline.setDepthFormat(window.getDepthFormat());
		pipeline.addAttribute(Pipeline.Attribute.Position, Format.RGf32, 0, 0, 8);
		if(!pipeline.loadShaderGLSL(Shader.Type.Vertex, "main.shader", "VERTEX_SHADER=1")) return;
		if(!pipeline.loadShaderGLSL(Shader.Type.Fragment, "main.shader", "FRAGMENT_SHADER=1")) return;
		if(!pipeline.create()) return;
		
		// geometry vertices
		Vector2f[] vertices = new Vector2f[] { new Vector2f(3.0f, -1.0f), new Vector2f(-1.0f, -1.0f), new Vector2f(-1.0f, 3.0f) };
		
		// geometry indices
		short[] indices = new short[] { 0, 1, 2 };
		
		////////////////////////////////
		// interface test
		////////////////////////////////
		
		// create canvas
		Canvas canvas = new Canvas();
		
		// create root controls
		ControlRoot root = new ControlRoot(canvas, true);
		root.setFontSize(24);
		
		// create rect
		ControlRect rect = new ControlRect(root.ref(), CanvasElement.Mode.Texture);
		rect.setAlign(Control.Align.Expand);
		rect.setFullscreen(true);
		
		// create dialog
		ControlDialog dialog = new ControlDialog(root.ref(), 1, 8.0f, 8.0f);
		dialog.setUpdatedCallback(new ControlDialog.UpdatedCallback() {
			public void run(ControlDialog dialog) {
				int x = (int)dialog.getPositionX();
				int y = (int)dialog.getPositionY();
				int width = (int)dialog.getWidth();
				int height = (int)dialog.getHeight();
				Log.printf(Log.Level.Message, "Dialog Updated %d %d %dx%d\n", x, y, width, height);
			}
		});
		dialog.setAlign(Control.Align.Center);
		dialog.setSize(240.0f, 180.0f);
		
		// create text
		ControlText text = new ControlText(dialog.ref(), title);
		text.setAlign(Control.Align.CenterX);
		
		// create button
		ControlButton button = new ControlButton(dialog.ref(), "Button");
		button.setClickedCallback(new ControlButton.ClickedCallback() {
			public void run(ControlButton button) {
				Log.printf(Log.Level.Message, "%s Clicked\n", button.getText());
			}
		});
		button.setAlign(Control.Align.Expand);
		button.setMargin(0.0f, 0.0f, 0.0f, 16.0f);
		
		// create sliders
		Color color = Color.white();
		ControlSlider slider_r = new ControlSlider(dialog.ref(), "R", 2, color.r(), 0.0, 1.0);
		ControlSlider slider_g = new ControlSlider(dialog.ref(), "G", 2, color.g(), 0.0, 1.0);
		ControlSlider slider_b = new ControlSlider(dialog.ref(), "B", 2, color.b(), 0.0, 1.0);
		slider_r.setChangedCallback(new ControlSlider.ChangedCallback() { public void run(ControlSlider slider) { color.setR(slider.getValuef32()); } });
		slider_g.setChangedCallback(new ControlSlider.ChangedCallback() { public void run(ControlSlider slider) { color.setG(slider.getValuef32()); } });
		slider_b.setChangedCallback(new ControlSlider.ChangedCallback() { public void run(ControlSlider slider) { color.setB(slider.getValuef32()); } });
		slider_r.setAlign(Control.Align.ExpandX);
		slider_g.setAlign(Control.Align.ExpandX);
		slider_b.setAlign(Control.Align.ExpandX);
		
		////////////////////////////////
		// scene test
		////////////////////////////////
		
		// main async
		Async main_async = new Async();
		if(!main_async.init()) return;
		
		// process async
		Async process_async = new Async();
		if(!process_async.init()) return;
		
		// manager cache
		SceneManager.setShaderCache("shader.cache");
		SceneManager.setTextureCache("texture.cache");
		
		// create scene manager
		SceneManager scene_manager = new SceneManager();
		if(!scene_manager.create(device, SceneManager.Flags.DefaultFlags, new SceneManager.CreateCallback() {
			public void run(int progress) {
				Log.printf(Log.Level.Message, "SceneManager %d%%   \r", progress);
			}
		}, main_async)) return;
		Log.print("\n");
		
		// process thread
		Thread process_thread = new Thread() {
			public void run() {
				while(scene_manager.isValidPtr() && !scene_manager.isTerminated()) {
					if(!scene_manager.process(process_async)) Time.sleep(1000);
				}
				Log.print(Log.Level.Message, "Thread Done\n");
			}
		};
		process_thread.start();
		
		////////////////////////////////
		// render test
		////////////////////////////////
		
		// create render manager
		RenderManager render_manager = new RenderManager(scene_manager);
		render_manager.setDrawParameters(device, window.getColorFormat(), window.getDepthFormat(), window.getMultisample());
		if(!render_manager.create(device)) return;
		if(!render_manager.create(device, RenderManager.Flags.DefaultFlags, new RenderManager.CreateCallback() {
			public void run(int progress) {
				Log.printf(Log.Level.Message, "RenderManager %d%%   \r", progress);
			}
		}, main_async)) return;
		Log.print("\n");
		
		// create render frame
		RenderFrame render_frame = new RenderFrame(render_manager);
		
		// render resources
		RenderRenderer render_renderer = render_manager.getRenderer();
		RenderSpatial render_spatial = render_manager.getSpatial();
		
		////////////////////////////////
		// scene test
		////////////////////////////////
		
		// create scene
		Scene scene = new Scene(scene_manager, render_renderer.getSceneRender());
		
		// create graph
		Graph graph = new Graph(scene);
		
		// create camera
		Vector3d camera_position = new Vector3d(12.0, 12.0, 6.0);
		CameraPerspective camera = new CameraPerspective(scene);
		NodeCamera node_camera = new NodeCamera(graph, camera);
		node_camera.setGlobalTransform(Matrix4x3d.placeTo(camera_position, new Vector3d(0.0), new Vector3d(0.0, 0.0, 1.0)));
		render_frame.setCamera(node_camera);
		
		// create light
		LightPoint light = new LightPoint(scene);
		NodeLight node_light = new NodeLight(graph, light);
		node_light.setGlobalTransform(Matrix4x3d.translate(camera_position));
		light.setIntensity(100.0f);
		light.setRadius(1000.0f);
		
		// create material
		Material metallic_material = new MaterialMetallic(scene);
		Material material = new Material(metallic_material.ref());
		material.setUniform("roughness_scale", 0.2f);
		material.setUniform("metallic_scale", 0.0f);
		
		// create object
		ObjectMesh object_mesh = new ObjectMesh(scene);
		NodeObject node_object = new NodeObject(graph, object_mesh);
		Mesh mesh = create_mesh(new Vector2i(64, 32), new Vector2f(8.0f, 2.0f), 2.0f);
		object_mesh.create(mesh, material);
		mesh.destroyPtr();
		
		////////////////////////////////
		// main loop
		////////////////////////////////
		
		// main loop
		window.run(new Window.MainLoopCallback() {
			
			int texture_frame = 0;
			double texture_ifps = 1.0 / 30.0;
			double texture_time = Time.seconds();
			
			public boolean run() {
				
				// update window
				Window.update();
				
				// render window
				if(!window.render()) return false;
				
				// update scene
				{
					// update render manager
					render_manager.update();
					
					// resize frame
					if(render_frame.getWidth() != window.getWidth() || render_frame.getHeight() != window.getHeight()) {
						if(!render_frame.create(device, render_renderer, window.getWidth(), window.getHeight())) return false;
						Log.printf(Log.Level.Message, "Frame Resized %dx%d\n", window.getWidth(), window.getHeight());
					}
					
					// update diffuse texture
					if(Time.seconds() - texture_time > texture_ifps) {
						texture_time += texture_ifps;
						Image image = create_image(256, texture_frame);
						material.setTexture("diffuse", "procedural", image);
						material.updateScene();
						texture_frame++;
					}
					
					// update graph
					double time = Time.seconds();
					node_object.setGlobalTransform(Matrix4x3d.rotateZ(time * 24.0).mul(Matrix4x3d.rotateX(time * 16.0)));
					node_object.updateScene();
					graph.updateSpatial();
					graph.updateScene();
					
					// update scene
					if(!scene.create(device, main_async)) return false;
					scene.setTime(time);
					scene.update(device);
					
					// update scene manager
					if(!scene_manager.update(device, main_async)) return false;
				}
				
				// dispatch
				{
					Compute compute = device.createCompute();
					
					// dispatch scene
					scene_manager.dispatch(device, compute);
					scene.dispatch(device, compute);
					
					// dispatch render (multi-frame test)
					RenderFrame[] render_frames = new RenderFrame[] { render_frame };
					render_spatial.dispatchFrames(compute, render_frames);
					render_spatial.dispatchObjects(compute, render_frames);
					render_renderer.dispatchFrames(compute, render_frames);
					
					compute.destroyPtr();
				}
				
				// draw
				{
					// flush buffers
					scene_manager.flush(device);
					render_manager.flush(device);
					render_frame.flush(device);
					
					// draw deferred
					render_renderer.drawDeferred(device, render_frame);
				}
				
				// dispatch
				{
					Compute compute = device.createCompute();
					
					// dispatch render
					render_renderer.dispatchLight(device, compute, render_frame);
					render_renderer.dispatchOccluder(device, compute, render_frame);
					render_renderer.dispatchLuminance(device, compute, render_frame);
					render_renderer.dispatchComposite(device, compute, render_frame);
					
					compute.destroyPtr();
				}
				
				// update interface
				{
					// window size
					float height = app.getHeight();
					float width = (float)Math.floor(height * window.getWidth() / window.getHeight());
					float mouse_x = width * window.getMouseX() / window.getWidth();
					float mouse_y = height * window.getMouseY() / window.getHeight();
					
					// mouse button
					int buttons = 0;
					if((window.getMouseButtons().value & Window.Button.Left.value) != 0) buttons |= Control.Button.Left.value;
					if((window.getMouseButtons().value & Window.Button.Left2.value) != 0) buttons |= Control.Button.Left.value;
					
					// render texture
					rect.setTexture(render_frame.getCompositeTexture(), true);
					rect.setTextureScale(width / window.getWidth(), height / window.getHeight());
					rect.setTextureFlip(false, render_renderer.isTargetFlipped());
					
					// update controls
					root.setViewport(width, height);
					root.setMouse(mouse_x, mouse_y, new Control.Button(buttons));
					while(root.update(canvas.getScale(target))) { }
					
					// create canvas
					canvas.create(device, target);
				}
				
				// window target
				target.begin();
				{
					Command command = device.createCommand(target);
					
					// common parameters
					float time = (float)Time.seconds();
					ByteBuffer parameters = ByteBuffer.allocate(64 + 32).order(ByteOrder.LITTLE_ENDIAN);
					parameters.put(Matrix4x4f.rotateZ(time * 16.0f).getBytes());
					parameters.put(color.getBytes());
					parameters.putFloat(time);
					
					// draw background
					command.setPipeline(pipeline);
					command.setUniform(0, parameters);
					command.setVertices(0, vertices);
					command.setIndices(indices);
					command.drawElements(3);
					
					// draw canvas
					canvas.draw(command, target);
					
					// destroy pointer
					command.destroyPtr();
				}
				target.end();
				
				// present window
				if(!window.present()) return false;
				
				// check device
				if(!device.check()) return false;
				
				// memory
				System.gc();
				
				return true;
			}
		});
		
		// stop process thread
		scene_manager.terminate();
		
		// finish context
		window.finish();
		
		// clear scene
		scene.clear();
		scene_manager.update(device, main_async);
		window.finish();
		
		// wait thread
		try {
			process_thread.join();
		} catch(Exception e) {
			System.out.println(e);
		}
		
		// destroy resources
		scene.destroyPtr();
		render_frame.destroyPtr();
		render_manager.destroyPtr();
		scene_manager.destroyPtr();
		root.destroyPtr();
		
		// keep window alive
		window.unacquirePtr();
		
		// done
		Log.print(Log.Level.Message, "Done\n");
	}
}
