/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.document.FeatureField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.elasticsearch.common.lucene.Lucene;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A {@link FieldMapper} that exposes Lucene's {@link FeatureField} as a sparse
 * vector of features.
 */
public class FeatureVectorFieldMapper extends FieldMapper {

    public static final String CONTENT_TYPE = "feature_vector";

    public static class Defaults {
        public static final MappedFieldType FIELD_TYPE = new FeatureVectorFieldType();

        static {
            FIELD_TYPE.setTokenized(false);
            FIELD_TYPE.setIndexOptions(IndexOptions.NONE);
            FIELD_TYPE.setHasDocValues(false);
            FIELD_TYPE.setOmitNorms(true);
            FIELD_TYPE.freeze();
        }
    }

    public static class Builder extends FieldMapper.Builder<Builder, FeatureVectorFieldMapper> {

        public Builder(String name) {
            super(name, Defaults.FIELD_TYPE, Defaults.FIELD_TYPE);
            builder = this;
        }

        @Override
        public FeatureVectorFieldType fieldType() {
            return (FeatureVectorFieldType) super.fieldType();
        }

        @Override
        public FeatureVectorFieldMapper build(BuilderContext context) {
            setupFieldType(context);
            return new FeatureVectorFieldMapper(
                    name, fieldType, defaultFieldType,
                    context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo);
        }
    }

    public static class TypeParser implements Mapper.TypeParser {
        @Override
        public Mapper.Builder<?,?> parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            FeatureVectorFieldMapper.Builder builder = new FeatureVectorFieldMapper.Builder(name);
            return builder;
        }
    }

    public static final class FeatureVectorFieldType extends MappedFieldType {

        public FeatureVectorFieldType() {
            setIndexAnalyzer(Lucene.KEYWORD_ANALYZER);
            setSearchAnalyzer(Lucene.KEYWORD_ANALYZER);
        }

        protected FeatureVectorFieldType(FeatureVectorFieldType ref) {
            super(ref);
        }

        public FeatureVectorFieldType clone() {
            return new FeatureVectorFieldType(this);
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }

        @Override
        public Query existsQuery(QueryShardContext context) {
            throw new UnsupportedOperationException("[feature_vector] fields do not support [exists] queries");
        }

        @Override
        public IndexFieldData.Builder fielddataBuilder(String fullyQualifiedIndexName) {
            throw new UnsupportedOperationException("[feature_vector] fields do not support sorting, scripting or aggregating");
        }

        @Override
        public Query termQuery(Object value, QueryShardContext context) {
            throw new UnsupportedOperationException("Queries on [feature_vector] fields are not supported");
        }
    }

    private FeatureVectorFieldMapper(String simpleName, MappedFieldType fieldType, MappedFieldType defaultFieldType,
                                Settings indexSettings, MultiFields multiFields, CopyTo copyTo) {
        super(simpleName, fieldType, defaultFieldType, indexSettings, multiFields, copyTo);
        assert fieldType.indexOptions().compareTo(IndexOptions.DOCS_AND_FREQS) <= 0;
    }

    @Override
    protected FeatureVectorFieldMapper clone() {
        return (FeatureVectorFieldMapper) super.clone();
    }

    @Override
    public FeatureVectorFieldType fieldType() {
        return (FeatureVectorFieldType) super.fieldType();
    }

    @Override
    public FieldMapper parse(ParseContext context) throws IOException {
        if (context.externalValueSet()) {
            throw new IllegalArgumentException("[feature_vector] fields can't be used in multi-fields");
        }

        if (context.parser().currentToken() != Token.START_OBJECT) {
            throw new IllegalArgumentException("[feature_vector] fields must be json objects, expected a START_OBJECT but got: " +
                    context.parser().currentToken());
        }

        String feature = null;
        for (Token token = context.parser().nextToken(); token != Token.END_OBJECT; token = context.parser().nextToken()) {
            if (token == Token.FIELD_NAME) {
                feature = context.parser().currentName();
            } else if (token == Token.VALUE_NULL) {
                // ignore feature, this is consistent with numeric fields
            } else if (token == Token.VALUE_NUMBER || token == Token.VALUE_STRING) {
                final String key = name() + "." + feature;
                float value = context.parser().floatValue(true);
                if (context.doc().getByKey(key) != null) {
                    throw new IllegalArgumentException("[feature_vector] fields do not support indexing multiple values for the same " +
                            "feature [" + key + "] in the same document");
                }
                context.doc().addWithKey(key, new FeatureField(name(), feature, value));
            } else {
                throw new IllegalArgumentException("[feature_vector] fields take hashes that map a feature to a strictly positive " +
                        "float, but got unexpected token " + token);
            }
        }
        return null; // no mapping update
    }

    @Override
    protected void parseCreateField(ParseContext context, List<IndexableField> fields) throws IOException {
        throw new AssertionError("parse is implemented directly");
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }

}
