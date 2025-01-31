package com.rtcsoft.sevakendra.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.rtcsoft.sevakendra.configs.StorageProperties;
import com.rtcsoft.sevakendra.controllers.UserController;
import com.rtcsoft.sevakendra.dtos.CustomerDTO;
import com.rtcsoft.sevakendra.entities.Customer;
import com.rtcsoft.sevakendra.exceptions.ApiException;
import com.rtcsoft.sevakendra.repositories.CustomerRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class CustomerService {

	@Autowired
	private CustomerRepository customerRepository;

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);

	private final Path rootLocation;

	private SharedService sharedService;

	@Autowired
	public CustomerService(StorageProperties properties, SharedService sharedService) {
		if (properties.getLocation().trim().isEmpty()) {
			throw new RuntimeException("File upload location cannot be empty.");
		}
		this.rootLocation = Paths.get(properties.getLocation());
		this.sharedService = sharedService;
	}

	private Customer mapDtoToCustomer(CustomerDTO input, Optional<Customer> existingCustomerOpt) {
		Customer customer = existingCustomerOpt.orElseGet(Customer::new);
		customer.setFirstName(Optional.ofNullable(input.getFirstName()).orElse(customer.getFirstName()));
		customer.setMiddleName(Optional.ofNullable(input.getMiddleName()).orElse(customer.getMiddleName()));
		customer.setLastName(Optional.ofNullable(input.getLastName()).orElse(customer.getLastName()));
		customer.setPhoneNumber(Optional.ofNullable(input.getPhoneNumber()).orElse(customer.getPhoneNumber()));
		customer.setAddress(Optional.ofNullable(input.getAddress()).orElse(customer.getAddress()));
		customer.setPlace(Optional.ofNullable(input.getPlace()).orElse(customer.getPlace()));
		customer.setAge(Optional.ofNullable(input.getAge()).orElse(customer.getAge()));
		customer.setCast(Optional.ofNullable(input.getCast()).orElse(customer.getCast()));
		customer.setOccupation(Optional.ofNullable(input.getOccupation()).orElse(customer.getOccupation()));
		customer.setAadharNumber(Optional.ofNullable(input.getAadharNumber()).orElse(customer.getAadharNumber()));
		customer.setImage(Optional.ofNullable(input.getImage()).orElse(customer.getImage()));
		customer.setUserId(input.getUserId() != 0 ? input.getUserId() : customer.getUserId());
		return customer;
	}

	public ResponseEntity<Customer> create(CustomerDTO input, MultipartFile file) throws ApiException, IOException {
		validateExistingEntry(input);
		Customer newCustomer = mapDtoToCustomer(input, Optional.empty());

		if (file != null && !file.isEmpty()) {
			newCustomer.setImage(storeFile(file, input));
		}

		customerRepository.save(newCustomer);
		return ResponseEntity.status(HttpStatus.CREATED).body(newCustomer);
	}

	public ResponseEntity<Customer> update(CustomerDTO input, MultipartFile file, long id)
			throws ApiException, IOException {
		Customer existingCustomer = customerRepository.findById(id)
				.orElseThrow(() -> new ApiException("Customer not found with id " + id));

		validateExistingEntry(input, id);
		Customer updatedCustomer = mapDtoToCustomer(input, Optional.of(existingCustomer));

		if (file != null && !file.isEmpty()) {
			updatedCustomer.setImage(storeFile(file, input));
		}

		updatedCustomer.setId(id);
		customerRepository.save(updatedCustomer);

		return ResponseEntity.status(HttpStatus.OK).body(updatedCustomer);
	}

	public ResponseEntity<List<Customer>> getAllUsers(HttpServletRequest request) {
		long authUserId = sharedService.getUserIdFromHeader(request);
		List<Customer> customers = customerRepository.findAllByUserId(authUserId);
		return ResponseEntity.ok(customers);
	}

	public ResponseEntity<Optional<Customer>> findById(long id) {
		Optional<Customer> customer = customerRepository.findById(id);
		return ResponseEntity.status(HttpStatus.OK).body(customer);
	}

	public ResponseEntity<Customer> delete(long id) throws ApiException {
		Customer existingCustomer = customerRepository.findById(id)
				.orElseThrow(() -> new ApiException("Customer not found with id " + id));
		customerRepository.deleteById(id);
		return ResponseEntity.status(HttpStatus.OK).body(existingCustomer);
	}

	private void validateExistingEntry(CustomerDTO input) throws ApiException {
		String fullName = buildFullName(input);
		List<Customer> existingCustomers = customerRepository.findByFullName(getTrimmedValue(input.getFirstName()),
				getTrimmedValue(input.getMiddleName()), getTrimmedValue(input.getLastName()));
		if (!existingCustomers.isEmpty()) {
			throw new ApiException("Customer already exists with this name: " + fullName);
		}
	}

	private void validateExistingEntry(CustomerDTO input, long id) throws ApiException {
		String fullName = buildFullName(input);
		List<Customer> existingCustomers = customerRepository.findByFullNameExcludingMe(id,
				getTrimmedValue(input.getFirstName()), getTrimmedValue(input.getMiddleName()),
				getTrimmedValue(input.getLastName()));
		if (!existingCustomers.isEmpty()) {
			throw new ApiException("Customer already exists with this name: " + fullName);
		}
	}

	private String storeFile(MultipartFile file, CustomerDTO input) throws IOException {
		String customerName = (getTrimmedValue(input.getFirstName()) + "-" + getTrimmedValue(input.getLastName()))
				.toLowerCase();
		Path destinationFile = rootLocation.resolve(Paths.get(file.getOriginalFilename())).normalize().toAbsolutePath();

		if (!destinationFile.getParent().equals(rootLocation.toAbsolutePath())) {
			throw new RuntimeException("Cannot store file outside the current directory.");
		}

		Path newDestinationFile = addPrefixToFile(destinationFile, customerName);
		try (InputStream inputStream = file.getInputStream()) {
			Files.copy(inputStream, newDestinationFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return newDestinationFile.toString();
	}

	private static String getTrimmedValue(String value) {
		return Optional.ofNullable(value).orElse("").trim();
	}

	private static String buildFullName(CustomerDTO input) {
		return Stream.of(input.getFirstName(), input.getMiddleName(), input.getLastName()).filter(Objects::nonNull)
				.map(String::trim).collect(Collectors.joining(" "));
	}

	public static Path addPrefixToFile(Path originalFilePath, String customerName) {
		String fileName = originalFilePath.getFileName().toString();
		String fileExtension = "";
		int lastDotIndex = fileName.lastIndexOf('.');
		if (lastDotIndex != -1) {
			fileExtension = fileName.substring(lastDotIndex);
			fileName = fileName.substring(0, lastDotIndex);
		}
		String newFileName = customerName + "-" + fileName + fileExtension;
		return originalFilePath.getParent().resolve(newFileName);
	}
}
