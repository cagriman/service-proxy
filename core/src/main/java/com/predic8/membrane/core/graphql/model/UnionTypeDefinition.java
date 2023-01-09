/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.graphql.model;

import com.predic8.membrane.core.graphql.ParsingException;
import com.predic8.membrane.core.graphql.Tokenizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.graphql.ParserUtil.*;
import static com.predic8.membrane.core.graphql.Tokenizer.Type.PUNCTUATOR;

public class UnionTypeDefinition implements TypeSystemDefinition {
    private String description;
    private String name;
    private List<Directive> directives;
    private List<NamedType> unionMemberTypes = new ArrayList<>();

    public UnionTypeDefinition() {
    }

    public UnionTypeDefinition(String description, String name, List<Directive> directives, List<NamedType> unionMemberTypes) {
        this.description = description;
        this.name = name;
        this.directives = directives;
        this.unionMemberTypes = unionMemberTypes;
    }

    @Override
    public void parse(Tokenizer tokenizer) throws IOException, ParsingException {

        name = parseName(tokenizer);

        if (!tokenizer.advance())
            return;

        directives = parseDirectivesConstOpt(tokenizer);
        if (directives != null) {
            if (!tokenizer.advance())
                return;
        }

        if (tokenizer.type() == PUNCTUATOR && tokenizer.integer() == '=') {
            unionMemberTypes = parseUnionMemberTypes(tokenizer);
        }

        tokenizer.revert();
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnionTypeDefinition that = (UnionTypeDefinition) o;
        return Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(directives, that.directives) && Objects.equals(unionMemberTypes, that.unionMemberTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name, directives, unionMemberTypes);
    }

    @Override
    public String toString() {
        return "UnionTypeDefinition{" +
                "description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", directives=" + directives +
                ", unionMemberTypes=" + unionMemberTypes +
                '}';
    }
}
