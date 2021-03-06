package com.fidecent.fbn.hx.mappers;

import com.fidecent.fbn.hx.domain.Event;
import com.fidecent.fbn.hx.domain.EventAttendee;
import com.fidecent.fbn.hx.dto.groups.DistributionGroupDto;
import com.google.gson.JsonElement;
import com.microsoft.graph.models.extensions.Attendee;
import com.microsoft.graph.models.extensions.Contact;
import com.microsoft.graph.models.extensions.ContactFolder;
import com.microsoft.graph.models.extensions.DateTimeTimeZone;
import com.microsoft.graph.models.extensions.DirectoryObject;
import com.microsoft.graph.models.extensions.EmailAddress;
import com.microsoft.graph.models.extensions.Group;
import com.microsoft.graph.models.extensions.ItemBody;
import com.microsoft.graph.models.generated.AttendeeType;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.extensions.IContactCollectionPage;
import com.microsoft.graph.requests.extensions.IContactFolderCollectionPage;
import com.microsoft.graph.requests.extensions.IDirectoryObjectCollectionWithReferencesPage;
import com.microsoft.graph.requests.extensions.IGroupCollectionPage;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mapper(componentModel = "spring")
public interface GraphMapper {
    String SORT_PARAM = "$orderby";
    String COUNT_PARAM = "$count";
    String PAGE_SIZE_PARAM = "$top";
    String OFFSET_PARAM = "$skip";

    List<DistributionGroupDto> mapGroups(List<Group> groups);

    List<DistributionGroupDto> mapContactFolder(List<ContactFolder> folders);

    default Optional<QueryOption> mapSortParam(Sort sort) {
        return Optional.ofNullable(sort).filter(Sort::isSorted)
                .map(Sort::get)
                .map(a -> a.map(s -> String.join(" ", s.getProperty(), s.getDirection().name())).collect(Collectors.joining(",")))
                .map(value -> new QueryOption(SORT_PARAM, value));
    }

    default List<QueryOption> mapPageable(Pageable pageable) {
        List<QueryOption> list = new ArrayList<>(4);
        if (pageable.isPaged()) {
            list.add(new QueryOption(COUNT_PARAM, true));
            list.add(new QueryOption(PAGE_SIZE_PARAM, pageable.getPageSize()));
            list.add(new QueryOption(OFFSET_PARAM, pageable.getOffset()));
        }
        mapSortParam(pageable.getSort()).ifPresent(list::add);
        return list;
    }

    default Page<DistributionGroupDto> mapGroupResponse(IGroupCollectionPage page, Pageable pageable) {
        List<DistributionGroupDto> data = mapGroups(page.getCurrentPage());
        return applyPagination(pageable, data);
    }

    default Page<DistributionGroupDto> mapContactFolderResponse(IContactFolderCollectionPage page, Pageable pageable) {
        List<DistributionGroupDto> data = mapContactFolder(page.getCurrentPage());
        return applyPagination(pageable, data);
    }

    private <T> Page<T> applyPagination(Pageable pageable, List<T> data) {
        int totalElements = data.size();
        if (pageable.isPaged()) {
            int fromIndex = (int) pageable.getOffset();
            int toIndex = fromIndex + pageable.getPageSize();
            if (toIndex < totalElements) {
                data = data.subList(fromIndex, toIndex);
            }
        }
        return new PageImpl<>(data, pageable, totalElements);
    }


    default Stream<EventAttendee> mapMemberMailsResponse(IDirectoryObjectCollectionWithReferencesPage source, String groupName) {
        List<DirectoryObject> page = source.getCurrentPage();
        return page.stream().map(DirectoryObject::getRawObject)
                .map(obj -> new EventAttendee(getStringValue(obj.get("mail")), getStringValue(obj.get("companyName")), groupName, getStringValue(obj.get("givenName")), getStringValue(obj.get("surname"))));
    }

    default List<EventAttendee> mapMemberMailsResponse(IContactCollectionPage source, String groupName) {
        List<Contact> page = source.getCurrentPage();
        Set<String> processed = new HashSet<>();
        return page.stream()
                .map(obj -> new EventAttendee(obj.emailAddresses.get(0).address, obj.companyName, groupName, obj.givenName, obj.surname))
                .filter(a -> processed.add(a.getEmail().toLowerCase())).collect(Collectors.toList());
    }

    default String getStringValue(JsonElement element) {
        return (element != null && !element.isJsonNull()) ? element.getAsString() : null;
    }

    default Attendee mapAttendee(String email, String name) {
        Attendee attendee = new Attendee();
        EmailAddress emailAddress = new EmailAddress();
        emailAddress.address = email;
        emailAddress.name = name;
        attendee.emailAddress = emailAddress;
        attendee.type = AttendeeType.REQUIRED;
        return attendee;
    }

    default DateTimeTimeZone mapDateTime(LocalDate date, LocalTime time, String timeZone) {
        DateTimeTimeZone dateTimeTimeZone = new DateTimeTimeZone();
        dateTimeTimeZone.dateTime = date.atTime(time).format(DateTimeFormatter.ISO_DATE_TIME);
        dateTimeTimeZone.timeZone = timeZone;
        return dateTimeTimeZone;
    }

    @SuppressWarnings("UnmappedTargetProperties")
    @Mapping(target = "subject", source = "title")
    @Mapping(target = "body", source = "additionalInfo")
    com.microsoft.graph.models.extensions.Event mapCalenderEvent(Event source);

    @SuppressWarnings("UnmappedTargetProperties")
    @Mapping(target = "content", source = ".")
    @Mapping(target = "contentType", constant = "HTML")
    ItemBody mapItemBody(String body);
}
