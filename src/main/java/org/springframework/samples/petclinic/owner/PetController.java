/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller responsible for managing Pet entities within the context of a
 * specific Owner. Handles HTTP requests for adding and editing pets, delegating
 * persistence to OwnerRepository. All routes are scoped under /owners/{ownerId}.
 *
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Wick Dynex
 */
@Controller
@RequestMapping("/owners/{ownerId}")
class PetController {

	private static final String VIEWS_PETS_CREATE_OR_UPDATE_FORM = "pets/createOrUpdatePetForm";

	private final OwnerRepository owners;

	private final PetTypeRepository types;

	public PetController(OwnerRepository owners, PetTypeRepository types) {
		this.owners = owners;
		this.types = types;
	}

	/**
	 * Populates the types model attribute with all available pet types for use in the pet
	 * creation and edit forms.
	 * @return a collection of all PetType options
	 */
	@ModelAttribute("types")
	public Collection<PetType> populatePetTypes() {
		return this.types.findPetTypes();
	}

	/**
	 * Resolves the Owner model attribute for the current request.
	 * @param ownerId the owner's database ID from the URL path variable
	 * @return the matching Owner from the repository
	 * @throws IllegalArgumentException if no owner exists with the given ID
	 */
	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable("ownerId") int ownerId) {
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		return optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
	}

	/**
	 * Resolves the Pet model attribute for the current request. Returns a new empty Pet
	 * for creation forms (when petId is absent), or loads the existing pet from the owner
	 * for edit forms.
	 * @param ownerId the owner's database ID from the URL path variable
	 * @param petId the pet's database ID, or null for new pet forms
	 * @return the resolved or newly constructed Pet
	 * @throws IllegalArgumentException if no owner exists with the given ID
	 */
	@ModelAttribute("pet")
	public Pet findPet(@PathVariable("ownerId") int ownerId,
			@PathVariable(name = "petId", required = false) Integer petId) {

		if (petId == null) {
			return new Pet();
		}

		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner.getPet(petId);
	}

	@InitBinder("owner")
	public void initOwnerBinder(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	@InitBinder("pet")
	public void initPetBinder(WebDataBinder dataBinder) {
		dataBinder.setValidator(new PetValidator());
		dataBinder.setDisallowedFields("id", "*.id");
	}

	@GetMapping("/pets/new")
	public String initCreationForm(Owner owner, ModelMap model) {
		Pet pet = new Pet();
		owner.addPet(pet);
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Processes the new pet form submission. Validates the pet data, checks for duplicate
	 * pet names under the same owner, and rejects future birth dates.
	 * @param owner the owner to associate the new pet with
	 * @param pet the pet to create, populated and validated from form input
	 * @param result binding and validation result
	 * @param redirectAttributes flash attributes for the redirect after successful save
	 * @return a redirect to the owner detail page on success, or the form view if errors
	 * exist
	 */
	@PostMapping("/pets/new")
	public String processCreationForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		if (StringUtils.hasText(pet.getName()) && pet.isNew() && owner.getPet(pet.getName(), true) != null) {
			result.rejectValue("name", "duplicate", "already exists");
		}

		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		owner.addPet(pet);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Pet has been Added");
		return "redirect:/owners/{ownerId}";
	}

	@GetMapping("/pets/{petId}/edit")
	public String initUpdateForm() {
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Processes the pet edit form submission. Validates updated pet data, checks for
	 * duplicate names, rejects future birth dates, then updates the existing pet record
	 * via updatePetDetails.
	 * @param owner the owner of the pet being edited
	 * @param pet the pet with updated field values from the form
	 * @param result binding and validation result
	 * @param redirectAttributes flash attributes for the redirect after successful update
	 * @return a redirect to the owner detail page on success, or the form view if errors
	 * exist
	 */
	@PostMapping("/pets/{petId}/edit")
	public String processUpdateForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		String petName = pet.getName();

		// checking if the pet name already exists for the owner
		if (StringUtils.hasText(petName)) {
			Pet existingPet = owner.getPet(petName, false);
			if (existingPet != null && !Objects.equals(existingPet.getId(), pet.getId())) {
				result.rejectValue("name", "duplicate", "already exists");
			}
		}

		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		updatePetDetails(owner, pet);
		redirectAttributes.addFlashAttribute("message", "Pet details has been edited");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Updates the pet details if it exists or adds a new pet to the owner.
	 * @param owner The owner of the pet
	 * @param pet The pet with updated details
	 */
	private void updatePetDetails(Owner owner, Pet pet) {
		Integer id = pet.getId();
		Assert.state(id != null, "'pet.getId()' must not be null");
		Pet existingPet = owner.getPet(id);
		if (existingPet != null) {
			// Update existing pet's properties
			existingPet.setName(pet.getName());
			existingPet.setBirthDate(pet.getBirthDate());
			existingPet.setType(pet.getType());
		}
		else {
			owner.addPet(pet);
		}
		this.owners.save(owner);
	}

}
