package com.jarscan.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameSanitizerTests {

    @Test
    void removesTraversalCharacters() {
        assertThat(FilenameSanitizer.sanitize("../../evil.jar")).isEqualTo(".._.._evil.jar");
        assertThat(FilenameSanitizer.sanitize("dir\\nested\\file.jar")).isEqualTo("dir_nested_file.jar");
    }
}
