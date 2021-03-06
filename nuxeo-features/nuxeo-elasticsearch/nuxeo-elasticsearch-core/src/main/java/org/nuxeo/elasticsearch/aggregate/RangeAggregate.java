/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     bdelbosc
 */
package org.nuxeo.elasticsearch.aggregate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.range.RangeBuilder;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.query.api.AggregateDefinition;
import org.nuxeo.ecm.platform.query.api.AggregateRangeDefinition;
import org.nuxeo.ecm.platform.query.core.BucketRange;

/**
 * @since 6.0
 */
public class RangeAggregate extends AggregateEsBase<BucketRange> {

    public RangeAggregate(AggregateDefinition definition, DocumentModel searchDocument) {
        super(definition, searchDocument);
    }

    @JsonIgnore
    @Override
    public RangeBuilder getEsAggregate() {
        RangeBuilder ret = AggregationBuilders.range(getId()).field(getField());
        for (AggregateRangeDefinition range : getRanges()) {
            if (range.getFrom() != null) {
                if (range.getTo() != null) {
                    ret.addRange(range.getKey(), range.getFrom(), range.getTo());
                } else {
                    ret.addUnboundedFrom(range.getKey(), range.getFrom());
                }
            } else if (range.getTo() != null) {
                ret.addUnboundedTo(range.getKey(), range.getTo());
            }
        }
        return ret;
    }

    @JsonIgnore
    @Override
    public OrFilterBuilder getEsFilter() {
        if (getSelection().isEmpty()) {
            return null;
        }
        OrFilterBuilder ret = FilterBuilders.orFilter();
        for (AggregateRangeDefinition range : getRanges()) {
            if (getSelection().contains(range.getKey())) {
                RangeFilterBuilder rangeFilter = FilterBuilders.rangeFilter(getField());
                if (range.getFrom() != null) {
                    rangeFilter.gte(range.getFrom());
                }
                if (range.getTo() != null) {
                    rangeFilter.lt(range.getTo());
                }
                ret.add(rangeFilter);
            }
        }
        return ret;
    }

    @JsonIgnore
    @Override
    public void parseEsBuckets(Collection<? extends MultiBucketsAggregation.Bucket> buckets) {
        List<BucketRange> nxBuckets = new ArrayList<>(buckets.size());
        for (MultiBucketsAggregation.Bucket bucket : buckets) {
            Range.Bucket rangeBucket = (Range.Bucket) bucket;
            nxBuckets.add(new BucketRange(bucket.getKey(), rangeBucket.getFrom(), rangeBucket.getTo(),
                    rangeBucket.getDocCount()));
        }
        Collections.sort(nxBuckets, new BucketRangeComparator());
        this.buckets = nxBuckets;
    }

    protected class BucketRangeComparator implements Comparator<BucketRange> {
        @Override
        public int compare(BucketRange arg0, BucketRange arg1) {
            return definition.getAggregateRangeDefinitionOrderMap().get(arg0.getKey()).compareTo(
                    definition.getAggregateRangeDefinitionOrderMap().get(arg1.getKey()));
        }
    }

}
