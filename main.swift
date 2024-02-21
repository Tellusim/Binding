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
func main() -> Int32 {
	
	// create window
	let window = Window(platform: Platform.any, index: Maxu32)
	if !window { return 1 }
	
	window.setCloseClickedCallback(Window.CloseClickedFunction({() in window.stop() }))
	window.setKeyboardPressedCallback(Window.KeyboardPressedFunction({ (key: UInt32, code: UInt32) in
		if key == Window.Key.esc.rawValue { window.stop() }
	}))
	
	let title = window.getPlatformName() + " Tellusim::Swift"
	if !window.create(title, Window.Flags.defaultFlags) || !window.setHidden(false) { return 1 }
	
	// create device
	let device = Device(window)
	if !device { return 1 }
	
	// device features
	print("Features: ", device.getFeatures())
	print("Device: ", device.getName())
	
	// create target
	let target = device.createTarget(window)
	if !target{ return 1 }
	
	// create pipeline
	let pipeline = device.createPipeline()
	pipeline.setUniformMask(0, Shader.Mask.fragment, true)
	pipeline.setColorFormat(window.getColorFormat(), 1)
	pipeline.setDepthFormat(window.getDepthFormat())
	pipeline.addAttribute(Pipeline.Attribute.position, Format.rgf32, 0, 0, 8, 0)
	if !pipeline.loadShaderGLSL(Shader.Kind.vertex, "main.shader", "VERTEX_SHADER=1") { return 1 }
	if !pipeline.loadShaderGLSL(Shader.Kind.fragment, "main.shader", "FRAGMENT_SHADER=1") { return 1 }
	if !pipeline.create() { return 1 }
	
	// geometry vertices
	var vertices: [Vector2f] = [ Vector2f(3.0, -1.0), Vector2f(-1.0, -1.0), Vector2f(-1.0, 3.0) ]
	
	// geometry indices
	var indices: [UInt16] = [ 0, 1, 2 ]
	
	// main loop
	window.run(Window.MainLoopFunction({ () -> Bool in
		
		// update window
		Window.update(false)
		
		// render window
		if !window.render() { return false }
		
		// window target
		if target.begin() {
			
			let command = device.createCommand(target)
			
			// current time
			let time = Float32(Time.seconds())
			
			struct Parameters {
				var transform: Matrix4x4f
				var color: Color
				var time: Float32
			}
			
			var parameters = Parameters(
				transform: Matrix4x4f.rotateZ(time * 16.0),
				color: Color(1.0),
				time: time
			)
			
			// draw background
			command.setPipeline(pipeline)
			command.setUniform(0, &parameters)
			command.setVertices(0, &vertices)
			command.setIndices(&indices)
			command.drawElements(3, 0, 0)
		}
		target.end()
		
		// present window
		if !window.present() { return false }
		
		// check device
		if !device.check() { return false }
		
		return true
	}))
	
	// finish context
	window.finish()
	
	// done
	print("Done")
	
	return 0
}

/*
 */
print(main())
