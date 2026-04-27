package com.miniagoda;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class HealthController {
	@GetMapping("/")
	public String home() {
		 return "miniAgoda API is running";
	}
}