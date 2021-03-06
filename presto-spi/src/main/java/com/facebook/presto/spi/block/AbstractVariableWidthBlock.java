/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.spi.block;

import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.VariableWidthType;
import io.airlift.slice.Slice;
import io.airlift.slice.Slices;

public abstract class AbstractVariableWidthBlock
        implements Block
{
    protected final VariableWidthType type;

    protected AbstractVariableWidthBlock(VariableWidthType type)
    {
        this.type = type;
    }

    protected abstract Slice getRawSlice();

    protected abstract int getPositionOffset(int position);

    protected abstract int getPositionLength(int position);

    protected abstract boolean isEntryNull(int position);

    @Override
    public Type getType()
    {
        return type;
    }

    @Override
    public BlockEncoding getEncoding()
    {
        return new VariableWidthBlockEncoding(type);
    }

    @Override
    public boolean getBoolean(int position)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLong(int position)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getDouble(int position)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getObjectValue(ConnectorSession session, int position)
    {
        if (isNull(position)) {
            return null;
        }
        return type.getObjectValue(session, getRawSlice(), getPositionOffset(position), getPositionLength(position));
    }

    @Override
    public Slice getSlice(int position)
    {
        if (isNull(position)) {
            throw new IllegalStateException("position is null");
        }
        return type.getSlice(getRawSlice(), getPositionOffset(position), getPositionLength(position));
    }

    @Override
    public Block getSingleValueBlock(int position)
    {
        if (isNull(position)) {
            return new VariableWidthBlock(type, 1, Slices.wrappedBuffer(new byte[0]), new int[] {0, 0}, new boolean[] {true});
        }

        int offset = getPositionOffset(position);
        int entrySize = getPositionLength(position);

        Slice copy = Slices.copyOf(getRawSlice(), offset, entrySize);

        return new VariableWidthBlock(type, 1, copy, new int[] {0, copy.length()}, new boolean[] {false});
    }

    @Override
    public boolean isNull(int position)
    {
        checkReadablePosition(position);
        return isEntryNull(position);
    }

    @Override
    public boolean equalTo(int position, Block otherBlock, int otherPosition)
    {
        boolean leftIsNull = isNull(position);
        boolean rightIsNull = otherBlock.isNull(otherPosition);

        if (leftIsNull != rightIsNull) {
            return false;
        }

        // if values are both null, they are equal
        if (leftIsNull) {
            return true;
        }

        return otherBlock.equalTo(otherPosition, getRawSlice(), getPositionOffset(position), getPositionLength(position));
    }

    @Override
    public boolean equalTo(int position, Slice otherSlice, int otherOffset, int otherLength)
    {
        checkReadablePosition(position);
        return type.equalTo(getRawSlice(), getPositionOffset(position), getPositionLength(position), otherSlice, otherOffset, otherLength);
    }

    @Override
    public int hash(int position)
    {
        if (isNull(position)) {
            return 0;
        }
        return type.hash(getRawSlice(), getPositionOffset(position), getPositionLength(position));
    }

    @Override
    public int compareTo(SortOrder sortOrder, int position, Block otherBlock, int otherPosition)
    {
        boolean leftIsNull = isNull(position);
        boolean rightIsNull = otherBlock.isNull(otherPosition);

        if (leftIsNull && rightIsNull) {
            return 0;
        }
        if (leftIsNull) {
            return sortOrder.isNullsFirst() ? -1 : 1;
        }
        if (rightIsNull) {
            return sortOrder.isNullsFirst() ? 1 : -1;
        }

        // compare the right block to our slice but negate the result since we are evaluating in the opposite order
        int result = -otherBlock.compareTo(otherPosition, getRawSlice(), getPositionOffset(position), getPositionLength(position));
        return sortOrder.isAscending() ? result : -result;
    }

    @Override
    public int compareTo(int position, Slice otherSlice, int otherOffset, int otherLength)
    {
        checkReadablePosition(position);
        return type.compareTo(getRawSlice(), getPositionOffset(position), getPositionLength(position), otherSlice, otherOffset, otherLength);
    }

    @Override
    public void appendTo(int position, BlockBuilder blockBuilder)
    {
        if (isNull(position)) {
            blockBuilder.appendNull();
        }
        else {
            type.appendTo(getRawSlice(), getPositionOffset(position), getPositionLength(position), blockBuilder);
        }
    }

    private void checkReadablePosition(int position)
    {
        if (position < 0 || position >= getPositionCount()) {
            throw new IllegalArgumentException("position is not valid");
        }
    }
}
