/*
 * =================================================
 * Mockifer License
 * =================================================
 *
 * MIT License
 *
 * Copyright (c) 2017 Marcel Braghetto
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

/*
 Return a formatted long string of a date. Example: 2017-07-29T16:01:02.691+12:00
*/
function mockifer_getFormattedDateLong(date) {
    var shortDate = mockifer_getFormattedDateShort(date);
    var hours = mockifer_paddedNumberString(date.getHours());
    var minutes = mockifer_paddedNumberString(date.getMinutes());
    var seconds = mockifer_paddedNumberString(date.getSeconds());

    var timeZoneOffset = -date.getTimezoneOffset();
    var timeZoneSymbol = timeZoneOffset >= 0 ? '+' : '-';
    var timeZoneHours = mockifer_paddedNumberString(timeZoneOffset / 60);
    var timeZoneMinutes = mockifer_paddedNumberString(timeZoneOffset % 60);

    return [shortDate, "T", hours, ":", minutes, ":", seconds, timeZoneSymbol, timeZoneHours, ":", timeZoneMinutes].join('');
}

/*
 Return a formatted short string of a date. Example: 2017-07-29
*/
function mockifer_getFormattedDateShort(date) {
    var year = date.getFullYear();
    var month = mockifer_paddedNumberString(date.getMonth() + 1);
    var day = mockifer_paddedNumberString(date.getDate());
    return [year, month, day].join('-');
}

/*
 Add (or subtract) a number of days from a given date.
*/
function mockifer_addDaysToDate(date, days) {
    var offsetInMilliseconds = days * 86400000;
    var updatedTime = date.getTime() + offsetInMilliseconds;
    return new Date(updatedTime);
}

/*
 Left pad a number if it is less than 10.
*/
function mockifer_paddedNumberString(number) {
    return (number > 9 ? "" : "0") + number;
}