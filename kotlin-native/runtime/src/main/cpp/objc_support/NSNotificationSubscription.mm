/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#if KONAN_MACOSX || KONAN_IOS || KONAN_TVOS || KONAN_WATCHOS

#include "NSNotificationSubscription.hpp"

#import <Foundation/NSNotification.h>

using namespace kotlin;

@interface Kotlin_objc_support_NSNotificationSubscriptionImpl : NSObject {
    NSNotificationCenter* center_;
    // TODO: Test that this is properly destructed.
    std::function<void()> handler_;
}

- (instancetype)initWithNotificationCenter:(NSNotificationCenter*)center
                                      name:(NSNotificationName)name
                                   handler:(std::function<void()>)handler;

- (void)reset;

- (void)onNotification:(NSNotification*)notification;

@end

@implementation Kotlin_objc_support_NSNotificationSubscriptionImpl

- (instancetype)initWithNotificationCenter:(NSNotificationCenter*)center
                                      name:(NSNotificationName)name
                                   handler:(std::function<void()>)handler {
    if ((self = [super init])) {
        center_ = center;
        handler_ = std::move(handler);

        [center_ addObserver:self selector:@selector(onNotification:) name:name object:nil];
    }
    return self;
}

- (void)reset {
    [center_ removeObserver:self];
}

- (void)onNotification:(NSNotification*)notification {
    handler_();
}

@end

objc_support::NSNotificationSubscription::NSNotificationSubscription(
        NSNotificationCenter* center, NSString* name, std::function<void()> handler) noexcept :
    impl_([[Kotlin_objc_support_NSNotificationSubscriptionImpl alloc] initWithNotificationCenter:center
                                                                                            name:name
                                                                                         handler:std::move(handler)]) {}

objc_support::NSNotificationSubscription::NSNotificationSubscription(NSString* name, std::function<void()> handler) noexcept :
    impl_([[Kotlin_objc_support_NSNotificationSubscriptionImpl alloc] initWithNotificationCenter:[NSNotificationCenter defaultCenter]
                                                                                            name:name
                                                                                         handler:std::move(handler)]) {}

void objc_support::NSNotificationSubscription::reset() noexcept {
    impl_ = nil;
}

bool objc_support::NSNotificationSubscription::subscribed() const noexcept {
    return impl_ != nil;
}

objc_support::NSNotificationSubscription::NSNotificationSubscription(NSNotificationSubscription&& rhs) noexcept : impl_(rhs.impl_) {
    rhs.impl_ = nil;
}

objc_support::NSNotificationSubscription& objc_support::NSNotificationSubscription::operator=(NSNotificationSubscription&& rhs) noexcept {
    NSNotificationSubscription tmp(std::move(rhs));
    swap(tmp);
    return *this;
}

void objc_support::NSNotificationSubscription::swap(NSNotificationSubscription& rhs) noexcept {
    using std::swap;
    swap(impl_, rhs.impl_);
}

#endif
