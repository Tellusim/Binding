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

use tellusim::*;
use std::process::exit;

extern crate tellusim;

/*
 */
fn create_mesh(steps: &Vector2u, radius: &Vector2f, texcoord: f32) -> Mesh {
	
	// create mesh
	let mut mesh = Mesh::new();
	
	// create vertices
	let num_vertices = (steps.x + 1) * (steps.y + 1);
	let mut positions = MeshAttribute::new_with_type_size(MeshAttributeType::Position, Format::RGBf32, num_vertices, 0);
	let mut normals = MeshAttribute::new_with_type_size(MeshAttributeType::Normal, Format::RGBf32, num_vertices, 0);
	let mut tangents = MeshAttribute::new_with_type_size(MeshAttributeType::Tangent, Format::RGBAf32, num_vertices, 0);
	let mut texcoords = MeshAttribute::new_with_type_size(MeshAttributeType::TexCoord, Format::RGf32, num_vertices, 0);
	
	let mut vertex: u32 = 0;
	let isteps = Vector2f::new_s(1.0) / Vector2f::new_v2u(steps);
	let aspect = radius.x / radius.y;
	for j in 0 .. steps.y + 1 {
		let ty = j as f32 * isteps.y;
		let z = -f32::cos(ty * PI2 - PI05);
		let r = f32::sin(ty * PI2 - PI05);
		for i in 0 .. steps.x + 1 {
			let tx = i as f32 * isteps.x;
			let x = -f32::sin(tx * PI2);
			let y = f32::cos(tx * PI2);
			positions.set(vertex, Vector3f::new(x * (r * radius.y + radius.x), y * (r * radius.y + radius.x), z * radius.y));
			normals.set(vertex, Vector3f::new(x * r, y * r, z));
			tangents.set(vertex, Vector4f::new(-y, x, 0.0, 1.0));
			texcoords.set(vertex, Vector2f::new(tx * aspect, ty) * texcoord);
			vertex += 1;
		}
	}
	
	let mut basis = MeshAttribute::new_with_type_size(MeshAttributeType::Basis, Format::RGBAu32, num_vertices, 0);
	basis.pack_attributes(&normals, &tangents, Format::RGBAf16);
	
	// create indices
	let num_indices = steps.x * steps.y * 4;
	let indices_format = if num_vertices < MAXU16 { Format::Ru16 } else { Format::Ru32 };
	let mut indices = MeshIndices::new_with_type_size(MeshIndicesType::Quadrilateral, indices_format, num_indices);
	
	let mut index = 0;
	for j in 0 .. steps.y {
		for i in 0 .. steps.x {
			vertex = (steps.x + 1) * j + i;
			indices.set4(index, vertex, vertex + 1, vertex + steps.x + 2, vertex + steps.x + 1);
			index += 4;
		}
	}
	
	// create geometry
	let mut geometry = MeshGeometry::new_with_mesh(&mut mesh);
	geometry.add_attribute_with_indices(&mut positions, &mut indices);
	geometry.add_attribute_with_indices(&mut basis, &mut indices);
	geometry.add_attribute_with_indices(&mut normals, &mut indices);
	geometry.add_attribute_with_indices(&mut texcoords, &mut indices);
	
	// geometry bounds
	let hsize = Vector3f::new_v2(&Vector2f::new_s(radius.x + radius.y), radius.y);
	geometry.set_bound_box(&BoundBoxf::new(&-hsize, &hsize));
	
	// create bounds
	mesh.create_bounds();
	
	mesh
}

/*
 */
fn create_image(size: u32, frame: u32) -> Image {
	
	// create image
	let mut image = Image::new();
	image.create2d_with_size(Format::RGBAu8n, size);
	
	// create sampler
	let mut sampler = ImageSampler::new_with_image_mut(&mut image);
	
	// fill image
	let mut color = ImageColor::new_s(255);
	for y in 0 .. size {
		for x in 0 .. size {
			let v = (((x as i32 - (frame ^ y) as i32) as u32 ^ (y + (frame ^ x))) & 255) as f32 / 63.0;
			color.r = (f32::cos(PI * 1.0 + v) * 127.5 + 127.5) as u32;
			color.g = (f32::cos(PI * 0.5 + v) * 127.5 + 127.5) as u32;
			color.b = (f32::cos(PI * 0.0 + v) * 127.5 + 127.5) as u32;
			sampler.set2d(x, y, &color);
		}
	}
	
	image
}

/*
 */
fn main() {
	
	// create app
	let mut app = App::new();
	if !app.create() { exit(1) }
	
	let mut window = Window::new_with_platform_index(app.platform(), app.device());
	if !window.is_valid_ptr() { exit(1) }
	
	window.set_size(app.width(), app.height());
	
	window.set_close_clicked_callback({
		let mut window = window.copy_ptr();
		move || { window.stop() }
	});
	
	window.set_keyboard_pressed_callback({
		let mut window = window.copy_ptr();
		move |key: u32, _code: u32| {
			if key == WindowKey::Esc as u32 { window.stop() }
			if key == WindowKey::F12 as u32 {
				let mut image = Image::new();
				if window.grab(&mut image) && image.save("screenshot.png") {
					ts_log!(Message, "Screenshot\n");
				}
			}
		}
	});
	
	let title = window.platform_name() + &" Tellusim::Rust";
	if !window.create_with_title_flags(&title, WindowFlags::DefaultFlags | WindowFlags::VerticalSync) || !window.set_hidden(false) { exit(1) }
	
	// create device
	let device = Device::new_with_window(&mut window);
	if !device.is_valid_ptr() { exit(1) }
	
	// device features
	ts_logf!(Message, "Features:\n{0}\n", device.features());
	ts_logf!(Message, "Device: {0}\n", device.name());
	
	// build info
	ts_logf!(Message, "Build: {0}\n", App::build_info());
	
	// create target
	let mut target = device.create_target_with_window(&mut window);
	if !target.is_valid_ptr() { exit(1) }
	
	////////////////////////////////
	// core test
	////////////////////////////////
	
	let mut blob = Blob::new();
	blob.write_string_with_str(&title);
	
	blob.seek(0);
	ts_logf!(Message, "Stream: {0}\n", blob.read_string());
	
	////////////////////////////////
	// platform test
	////////////////////////////////
	
	// create pipeline
	let mut pipeline = device.create_pipeline();
	pipeline.set_uniform_mask(0, ShaderMask::Fragment);
	pipeline.set_color_format(0, window.color_format());
	pipeline.set_depth_format(window.depth_format());
	pipeline.add_attribute(PipelineAttribute::Position, Format::RGf32, 0, 0, 8);
	if !pipeline.load_shader_glsl(ShaderType::Vertex, "main.shader", "VERTEX_SHADER=1") { exit(1) }
	if !pipeline.load_shader_glsl(ShaderType::Fragment, "main.shader", "FRAGMENT_SHADER=1") { exit(1) }
	if !pipeline.create() { exit(1) }
	
	// geometry vertices
	let vertices: [Vector2f; 3] = [ Vector2f::new(3.0, -1.0), Vector2f::new(-1.0, -1.0), Vector2f::new(-1.0, 3.0) ];
	
	// geometry indices
	let indices: [u16; 3] = [ 0, 1, 2 ];
	
	////////////////////////////////
	// interface test
	////////////////////////////////
	
	// create canvas
	let mut canvas = Canvas::new();
	
	// create root controls
	let mut root = ControlRoot::new_with_canvas_blob(&mut canvas, true);
	root.set_font_size(24);
	
	// create rect
	let mut rect = ControlRect::new_with_parent_mode(Some(&root.to_control()), CanvasElementMode::Texture);
	rect.set_align(ControlAlign::Expand);
	rect.set_fullscreen(true);
	
	// create dialog
	let mut dialog = ControlDialog::new_with_parent_columns_x(Some(&root.to_control()), 1, 8.0, 8.0);
	dialog.set_updated_callback(|dialog: ControlDialog| {
		let x = dialog.position_x();
		let y = dialog.position_y();
		let width = dialog.width();
		let height = dialog.height();
		ts_logf!(Message, "Dialog Updated {0} {1} {2}x{3}\n", x, y, width, height);
	});
	dialog.set_align(ControlAlign::Center);
	dialog.set_size_with_width(240.0, 180.0);
	
	// create text
	let mut text = ControlText::new_with_parent_text(Some(&dialog.to_control()), &title);
	text.set_align(ControlAlign::CenterX);
	
	// create button
	let mut button = ControlButton::new_with_parent_text(Some(&dialog.to_control()), "Button");
	button.set_clicked_callback(move |button: ControlButton| {
		ts_logf!(Message, "{0} Clicked\n", button.text());
	});
	button.set_align(ControlAlign::Expand);
	button.set_margin_with_left(0.0, 0.0, 0.0, 16.0);
	
	// common parameters
	#[repr(C)]
	#[derive(Default)]
	struct CommonParameters {
		transform: Matrix4x4f,
		color: Color,
		time: f32,
	}
	let mut parameters = CommonParameters::default();
	parameters.color = Color::new_s(1.0);
	
	// create sliders
	let mut slider_r = ControlSlider::new_with_parent_text_digits_value_min(Some(&dialog.to_control()), "R", 2, parameters.color.r as f64, 0.0, 1.0);
	let mut slider_g = ControlSlider::new_with_parent_text_digits_value_min(Some(&dialog.to_control()), "G", 2, parameters.color.g as f64, 0.0, 1.0);
	let mut slider_b = ControlSlider::new_with_parent_text_digits_value_min(Some(&dialog.to_control()), "B", 2, parameters.color.b as f64, 0.0, 1.0);
	slider_r.set_changed_callback(|slider: ControlSlider| { parameters.color.r = slider.valuef32() });
	slider_g.set_changed_callback(|slider: ControlSlider| { parameters.color.g = slider.valuef32() });
	slider_b.set_changed_callback(|slider: ControlSlider| { parameters.color.b = slider.valuef32() });
	slider_r.set_align(ControlAlign::ExpandX);
	slider_g.set_align(ControlAlign::ExpandX);
	slider_b.set_align(ControlAlign::ExpandX);
	
	////////////////////////////////
	// scene test
	////////////////////////////////
	
	// main async
	let mut main_async = Async::new();
	if !main_async.init() { exit(1) }
	
	// process async
	let mut process_async = Async::new();
	if !process_async.init() { exit(1) }
	
	// manager cache
	SceneManager::set_shader_cache("shader.cache");
	SceneManager::set_texture_cache("texture.cache");
	
	// create scene manager
	let mut scene_manager = SceneManager::new();
	if cfg!(debug_assertions) {
		if !scene_manager.create_with_flags_func(&device, SceneManagerFlags::DefaultFlags, |progress: u32| { ts_logf!(Message, "SceneManager {0}%   \r", progress) }) { exit(1) }
		log::print("\n");
	} else {
		if !scene_manager.create(&device) { exit(1) }
	}
	
	// process thread
	let process_thread = std::thread::spawn({
		let mut scene_manager = scene_manager.copy_ptr();
		let mut process_async = process_async.copy_ptr();
		move || {
			while scene_manager.is_valid_ptr() && !scene_manager.is_terminated() {
				if !scene_manager.process(&mut process_async) { time::sleep(1000) }
			}
			ts_log!(Message, "Thread Done\n");
		}
	});
	
	////////////////////////////////
	// render test
	////////////////////////////////
	
	// create render manager
	let mut render_manager = RenderManager::new_with_manager(&mut scene_manager);
	render_manager.set_draw_parameters_with_color_depth_multisample(&device, window.color_format(), window.depth_format(), window.multisample());
	if cfg!(debug_assertions) {
		if !render_manager.create_with_flags_func(&device, RenderManagerFlags::DefaultFlags, |progress: u32| { ts_logf!(Message, "RenderManager {0}%   \r", progress) }) { exit(1) }
		log::print("\n");
	} else {
		if !render_manager.create(&device) { exit(1) }
	}
	
	// create render frame
	let mut render_frame = RenderFrame::new_with_manager(&mut render_manager);
	
	// render resources
	let mut render_renderer = render_manager.renderer();
	let mut render_spatial = render_manager.spatial();
	
	////////////////////////////////
	// scene test
	////////////////////////////////
	
	// create scene
	let mut scene = Scene::new_with_manager_render(&mut scene_manager, Some(&render_renderer.scene_render()));
	
	// create graph
	let mut graph = Graph::new_with_scene(&mut scene);
	
	// create camera
	let camera_position = Vector3d::new(12.0, 12.0, 6.0);
	let camera = CameraPerspective::new_with_scene(&mut scene);
	let mut node_camera = NodeCamera::new_with_graph_camera(&mut graph, &mut camera.to_camera());
	node_camera.set_global_transform(&Matrix4x3d::place_to(&camera_position, &Vector3d::new_s(0.0), &Vector3d::new(0.0, 0.0, 1.0)));
	render_frame.set_camera(&mut node_camera);
	
	// create light
	let mut light = LightPoint::new_with_scene(&mut scene);
	let mut node_light = NodeLight::new_with_graph_light(&mut graph, &mut light.to_light());
	node_light.set_global_transform(&Matrix4x3d::translate_v3(&camera_position));
	light.set_intensity(100.0);
	light.set_radius(1000.0);
	
	// create material
	let metallic_material = MaterialMetallic::new_with_scene(&mut scene);
	let mut material = Material::new_with_parent(Some(&metallic_material.to_material()));
	material.set_uniform_with_name_valuef32("roughness_scale", 0.2);
	material.set_uniform_with_name_valuef32("metallic_scale", 0.0);
	
	// create object
	let mut object_mesh = ObjectMesh::new_with_scene(&mut scene);
	let mut node_object = NodeObject::new_with_graph_object(&mut graph, &mut object_mesh.to_object());
	let mesh = create_mesh(&Vector2u::new(64, 32), &Vector2f::new(8.0, 2.0), 2.0);
	object_mesh.create_with_mesh_material(&mesh, Some(&material));
	
	////////////////////////////////
	// main loop
	////////////////////////////////
	
	let mut texture_frame = 0;
	let texture_ifps = 1.0 / 30.0;
	let mut texture_time = time::seconds();
	
	// main loop
	window.run({
		let device = device.copy_ptr();
		let mut window = window.copy_ptr();
		let mut scene = scene.copy_ptr();
		let mut scene_manager = scene_manager.copy_ptr();
		let mut main_async = main_async.copy_ptr();
		let parameters = &mut parameters;
		move || -> bool {
		
		// update window
		Window::update();
		
		// render window
		if !window.render() { return false }
		
		// update scene
		{
			// update render manager
			render_manager.update();
			
			// resize frame
			if render_frame.width() != window.width() || render_frame.height() != window.height() {
				if !render_frame.create(&device, &render_renderer, window.width(), window.height()) { return true }
				ts_logf!(Message, "Frame Resized {0}x{1}\n", window.width(), window.height());
			}
			
			// update diffuse texture
			if time::seconds() - texture_time > texture_ifps {
				texture_time += texture_ifps;
				let mut image = create_image(256, texture_frame);
				material.set_texture_with_name_hash_image("diffuse", "procedural", &mut image);
				material.update_scene();
				texture_frame += 1;
			}
			
			// update graph
			let time = time::seconds();
			node_object.set_global_transform(&(Matrix4x3d::rotate_z(time * 24.0) * Matrix4x3d::rotate_x(time * 16.0)));
			node_object.update_scene();
			graph.update_spatial();
			graph.update_scene();
			
			// update scene
			if !scene.create_with_async(&device, Some(&main_async)) { return false }
			scene.set_time(time);
			scene.update(&device);
			
			// update scene manager
			if !scene_manager.update(&device, &mut main_async) { return false }
		}
		
		// dispatch
		{
			let mut compute = device.create_compute();
			
			// dispatch scene
			scene_manager.dispatch(&device, &mut compute);
			scene.dispatch(&device, &mut compute);
			
			// dispatch render (multi-frame test)
			let render_frames: [RenderFrame; 1] = [ render_frame.copy_ptr() ];
			render_spatial.dispatch_frames(&mut compute, &render_frames);
			render_spatial.dispatch_objects_with_frames(&mut compute, &render_frames);
			render_renderer.dispatch_frames(&mut compute, &render_frames);
		}
		
		// draw
		{
			// flush buffers
			scene_manager.flush(&device);
			render_manager.flush(&device);
			render_frame.flush(&device);
			
			// draw deferred
			render_renderer.draw_deferred(&device, &mut render_frame);
		}
		
		// dispatch
		{
			let mut compute = device.create_compute();
			
			// dispatch render
			render_renderer.dispatch_light(&device, &mut compute, &mut render_frame);
			render_renderer.dispatch_occluder(&device, &mut compute, &mut render_frame);
			render_renderer.dispatch_luminance(&device, &mut compute, &mut render_frame);
			render_renderer.dispatch_composite(&device, &mut compute, &mut render_frame);
		}
		
		// update interface
		{
			// window size
			let height = app.height() as f32;
			let width = (height * window.width() as f32 / window.height() as f32).floor();
			let mouse_x = width * window.mouse_x() as f32 / window.width() as f32;
			let mouse_y = height * window.mouse_y() as f32 / window.height() as f32;
			
			// mouse button
			let mut buttons = ControlButtons::None;
			if window.mouse_buttons().has_flag(WindowButton::Left | WindowButton::Left2) { buttons |= ControlButtons::Left }
			
			// render texture
			rect.set_texture_with_linear(&mut render_frame.composite_texture(), true);
			rect.set_texture_scale(width / window.width() as f32, height / window.height() as f32);
			rect.set_texture_flip(false, render_renderer.is_target_flipped());
			
			// update controls
			root.set_viewportf32(width, height);
			root.set_mousef32(mouse_x, mouse_y, buttons);
			while root.update_with_scale(canvas.scale(&target)) { }
			
			// create canvas
			canvas.create(&device, &target);
		}
		
		// window target
		target.begin();
		{
			let mut command = device.create_command_with_target(&mut target);
			
			// current time
			let time = time::seconds() as f32;
			
			// common parameters
			parameters.transform = Matrix4x4f::rotate_z(time * 16.0);
			parameters.time = time;
			
			// draw background
			command.set_pipeline(&mut pipeline);
			command.set_uniform(0, parameters);
			command.set_vertices(0, &vertices);
			command.set_indices(&indices);
			command.draw_elements(3);
			
			// draw canvas
			canvas.draw_with_target(&mut command, &mut target);
		}
		target.end();
		
		// present window
		if !window.present() { return false }
		
		// check device
		if !device.check() { return false }
		
		true
	}});
	
	// stop process thread
	scene_manager.terminate();
	
	// clear scene
	scene.clear();
	scene_manager.update(&device, &mut main_async);
	window.finish();
	
	// wait thread
	process_thread.join().ok();
	
	// done
	log::print("Done\n");
}
