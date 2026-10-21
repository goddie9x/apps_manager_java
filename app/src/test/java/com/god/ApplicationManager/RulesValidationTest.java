package com.god.ApplicationManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.god.ApplicationManager.Facade.AppManagerFacade;
import com.god.ApplicationManager.Helper.SearchNormalizer;
import com.god.ApplicationManager.data.RulesTransfer;

import org.junit.Test;

/** Validation rules that guard shell commands and rule imports must never regress. */
public class RulesValidationTest {

    @Test
    public void validPackageNamesPass() {
        assertTrue(AppManagerFacade.isValidPackageName("com.example.app"));
        assertTrue(AppManagerFacade.isValidPackageName("com.god.ApplicationManager"));
        assertTrue(AppManagerFacade.isValidPackageName("a.b"));
        assertNull(RulesTransfer.validateRuleLine("org.fdroid.fdroid"));
    }

    @Test
    public void shellHostilePackageNamesAreRejected() {
        // Anything that could break out of a shell command line must fail validation.
        assertFalse(AppManagerFacade.isValidPackageName("com.example; rm -rf /"));
        assertFalse(AppManagerFacade.isValidPackageName("com.example app"));
        assertFalse(AppManagerFacade.isValidPackageName("com.example`id`"));
        assertFalse(AppManagerFacade.isValidPackageName("com.example$(id)"));
        assertFalse(AppManagerFacade.isValidPackageName(""));
        assertFalse(AppManagerFacade.isValidPackageName(null));
        assertNotNull(RulesTransfer.validateRuleLine("not a package"));
        assertNotNull(RulesTransfer.validateRuleLine(""));
        assertNotNull(RulesTransfer.validateRuleLine(null));
    }

    @Test
    public void searchNormalisationStripsVietnameseDiacritics() {
        assertEquals("dong bang", SearchNormalizer.normalize("Đóng băng"));
        assertEquals("ung dung", SearchNormalizer.normalize("Ứng dụng"));
    }
}
