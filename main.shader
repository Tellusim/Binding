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

#version 430 core

#if VERTEX_SHADER
	
	layout(location = 0) in vec2 in_position;
	
	layout(location = 0) out vec2 s_texcoord;
	
	/*
	 */
	void main() {
		
		vec2 texcoord = in_position;
		
		gl_Position = vec4(texcoord * 2.0f - 1.0f, 0.0f, 1.0f);
		
		#if CLAY_VK
			texcoord.y = 1.0f - texcoord.y;
		#endif
		
		s_texcoord = texcoord;
	}
	
#elif FRAGMENT_SHADER
	
	layout(row_major, binding = 0) uniform common_parameters {
		mat4 transform;
		vec4 color;
		float time;
	};
	
	layout(location = 0) in vec2 s_texcoord;
	
	layout(location = 0) out vec4 out_color;
	
	/*
	 */
	void main() {
		
		float k = time * 2.0f;
		
		vec2 t = (transform * vec4(s_texcoord * 32.0f - 16.0f, 0.0f, 0.0f)).xy;
		
		float v = sin(t.x + k) + sin(t.y + k) + sin(t.x + t.y + k) + sin(length(t) + k * 3.0f) + k * 2.0f;
		
		out_color = color * vec4(cos(vec3(0.0f, 0.5f, 1.0f) * 3.14f + v) * 0.5f + 0.5f, 1.0f);
	}
	
#endif
