/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.vfs.impl;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.MetadataSnapshot;
import org.gradle.internal.snapshot.PathSuffix;

import java.util.Optional;

import static org.gradle.internal.snapshot.SnapshotUtil.getSnapshotFromChild;
import static org.gradle.internal.snapshot.SnapshotUtil.invalidateSingleChild;
import static org.gradle.internal.snapshot.SnapshotUtil.storeSingleChild;

public class DefaultFileHierarchySet implements FileHierarchySet {

    @VisibleForTesting
    final FileSystemNode rootNode;
    private final CaseSensitivity caseSensitivity;

    public static FileHierarchySet from(String absolutePath, MetadataSnapshot snapshot, CaseSensitivity caseSensitivity) {
        PathSuffix relativePath = PathSuffix.of(absolutePath);
        return new DefaultFileHierarchySet(snapshot.asFileSystemNode(relativePath.getAsString()), caseSensitivity);
    }

    private DefaultFileHierarchySet(FileSystemNode rootNode, CaseSensitivity caseSensitivity) {
        this.rootNode = rootNode;
        this.caseSensitivity = caseSensitivity;
    }

    public static FileHierarchySet empty(CaseSensitivity caseSensitivity) {
        switch (caseSensitivity) {
            case CASE_SENSITIVE:
                return EmptyFileHierarchy.CASE_SENSITIVE;
            case CASE_INSENSITIVE:
                return EmptyFileHierarchy.CASE_INSENSITIVE;
            default:
                throw new AssertionError("Unknown case sensitivity: " + caseSensitivity);
        }
    }

    @Override
    public Optional<MetadataSnapshot> getMetadata(String absolutePath) {
        PathSuffix relativePath = PathSuffix.of(absolutePath);
        String pathToParent = rootNode.getPathToParent();
        if (!relativePath.hasPrefix(pathToParent, caseSensitivity)) {
            return Optional.empty();
        }
        return getSnapshotFromChild(rootNode, relativePath, caseSensitivity);
    }

    @Override
    public FileHierarchySet update(String absolutePath, MetadataSnapshot snapshot) {
        PathSuffix relativePath = PathSuffix.of(absolutePath);
        return new DefaultFileHierarchySet(storeSingleChild(rootNode, relativePath, caseSensitivity, snapshot), caseSensitivity);
    }

    @Override
    public FileHierarchySet invalidate(String absolutePath) {
        PathSuffix relativePath = PathSuffix.of(absolutePath);
        return invalidateSingleChild(rootNode, relativePath, caseSensitivity)
            .<FileHierarchySet>map(newRootNode -> new DefaultFileHierarchySet(newRootNode, caseSensitivity))
            .orElse(empty());
    }

    @Override
    public FileHierarchySet empty() {
        return empty(caseSensitivity);
    }

    private enum EmptyFileHierarchy implements FileHierarchySet {
        CASE_SENSITIVE(CaseSensitivity.CASE_SENSITIVE),
        CASE_INSENSITIVE(CaseSensitivity.CASE_INSENSITIVE);

        private final CaseSensitivity caseSensitivity;

        EmptyFileHierarchy(CaseSensitivity caseInsensitive) {
            this.caseSensitivity = caseInsensitive;
        }

        @Override
        public Optional<MetadataSnapshot> getMetadata(String path) {
            return Optional.empty();
        }

        @Override
        public FileHierarchySet update(String absolutePath, MetadataSnapshot snapshot) {
            return from(absolutePath, snapshot, caseSensitivity);
        }

        @Override
        public FileHierarchySet invalidate(String path) {
            return this;
        }

        @Override
        public FileHierarchySet empty() {
            return this;
        }
    }
}
